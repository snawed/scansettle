#!/usr/bin/env bash
#
# One-time AWS bootstrap for ScanSettle's non-production environment
# (docs/deployment.md, Section 1). Run this ONCE, by hand, with your own AWS
# credentials — everything after this is handled by GitHub Actions via OIDC,
# with no long-lived AWS keys stored anywhere.
#
# Easiest way to run this with zero local setup: open AWS CloudShell
# (console.aws.amazon.com -> the >_ icon, top right) in the eu-west-2 region,
# upload this file (or paste its contents), then:
#   chmod +x bootstrap.sh && ./bootstrap.sh
#
# What this creates:
#   1. An S3 bucket + DynamoDB table for Terraform remote state/locking.
#   2. A GitHub OIDC identity provider (if this AWS account doesn't have one
#      already — safe to run even if it does, GitHub's docs recommend one
#      provider per account, reused across all repos).
#   3. An IAM role ("scansettle-nonprod-deploy") that GitHub Actions assumes
#      via OIDC — trusted ONLY for the snawed/scansettle repo, no stored keys.
#   4. A policy on that role scoped to exactly what infra/terraform/nonprod
#      needs to manage (EC2, VPC read, IAM PassRole for the instance role,
#      SSM, ECR) — not AdministratorAccess.
#
# Idempotent-ish: safe to re-run if something fails partway; existing
# resources are detected and skipped rather than re-created.

set -euo pipefail

REGION="eu-west-2"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
GITHUB_REPO="snawed/scansettle"
GITHUB_REPO_OWNER="${GITHUB_REPO%%/*}"
GITHUB_REPO_NAME="${GITHUB_REPO##*/}"
STATE_BUCKET="scansettle-terraform-state-${ACCOUNT_ID}"
LOCK_TABLE="scansettle-terraform-locks"
ROLE_NAME="scansettle-nonprod-deploy"
OIDC_PROVIDER_URL="token.actions.githubusercontent.com"
OIDC_THUMBPRINT="6938fd4d98bab03faadb97b34396831e3780aea1" # GitHub's OIDC root CA thumbprint

echo "Account: ${ACCOUNT_ID}   Region: ${REGION}   Repo: ${GITHUB_REPO}"

# --- 1. Terraform state bucket -------------------------------------------------
if aws s3api head-bucket --bucket "${STATE_BUCKET}" 2>/dev/null; then
  echo "S3 bucket ${STATE_BUCKET} already exists — skipping."
else
  echo "Creating S3 bucket ${STATE_BUCKET}..."
  aws s3api create-bucket \
    --bucket "${STATE_BUCKET}" \
    --region "${REGION}" \
    --create-bucket-configuration LocationConstraint="${REGION}"
  aws s3api put-bucket-versioning \
    --bucket "${STATE_BUCKET}" \
    --versioning-configuration Status=Enabled
  aws s3api put-bucket-encryption \
    --bucket "${STATE_BUCKET}" \
    --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-public-access-block \
    --bucket "${STATE_BUCKET}" \
    --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
fi

# --- 2. Terraform lock table ----------------------------------------------------
if aws dynamodb describe-table --table-name "${LOCK_TABLE}" --region "${REGION}" >/dev/null 2>&1; then
  echo "DynamoDB table ${LOCK_TABLE} already exists — skipping."
else
  echo "Creating DynamoDB table ${LOCK_TABLE}..."
  aws dynamodb create-table \
    --table-name "${LOCK_TABLE}" \
    --region "${REGION}" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST
fi

# --- 3. GitHub OIDC provider -----------------------------------------------------
OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/${OIDC_PROVIDER_URL}"
if aws iam get-open-id-connect-provider --open-id-connect-provider-arn "${OIDC_ARN}" >/dev/null 2>&1; then
  echo "OIDC provider already exists — skipping."
else
  echo "Creating GitHub OIDC provider..."
  aws iam create-open-id-connect-provider \
    --url "https://${OIDC_PROVIDER_URL}" \
    --client-id-list "sts.amazonaws.com" \
    --thumbprint-list "${OIDC_THUMBPRINT}"
fi

# --- 4. IAM role for GitHub Actions (trust policy scoped to this one repo) -----
# Two things about the sub condition that aren't obvious from GitHub's own docs:
#
# 1. GitHub can issue the OIDC `sub` claim in TWO different formats:
#      repo:OWNER/REPO:ref:refs/heads/BRANCH                (classic)
#      repo:OWNER@id/REPO@id:ref:refs/heads/BRANCH           (ID-qualified)
#    The ID-qualified form is a GitHub hardening feature — the numeric IDs are
#    stable across a repo/owner rename, closing a hijack path where a stale
#    trust policy would otherwise still trust whoever renames into the old
#    name. Which form you get isn't something this script controls, so both
#    patterns are allowed below — matching only the classic form is a real,
#    silent trap: everything about the role/provider/policy can be completely
#    correct and it will still fail with the exact same generic
#    "Not authorized to perform sts:AssumeRoleWithWebIdentity" error.
#
# 2. aws-actions/configure-aws-credentials tags the assumed session with
#    GitHub context (repo/actor/workflow/etc.) by default, and AWS rejects the
#    *entire* AssumeRoleWithWebIdentity call — same generic error again, no
#    distinct message — if the trust policy doesn't also allow sts:TagSession.
TRUST_POLICY=$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "${OIDC_ARN}" },
    "Action": ["sts:AssumeRoleWithWebIdentity", "sts:TagSession"],
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike": {
        "token.actions.githubusercontent.com:sub": [
          "repo:${GITHUB_REPO}:*",
          "repo:${GITHUB_REPO_OWNER}@*/${GITHUB_REPO_NAME}@*:*"
        ]
      }
    }
  }]
}
EOF
)

if aws iam get-role --role-name "${ROLE_NAME}" >/dev/null 2>&1; then
  echo "IAM role ${ROLE_NAME} already exists — updating trust policy."
  aws iam update-assume-role-policy --role-name "${ROLE_NAME}" --policy-document "${TRUST_POLICY}"
else
  echo "Creating IAM role ${ROLE_NAME}..."
  aws iam create-role \
    --role-name "${ROLE_NAME}" \
    --assume-role-policy-document "${TRUST_POLICY}" \
    --description "Assumed by GitHub Actions (OIDC) to deploy ScanSettle's non-prod environment"
fi

# --- 5. Permissions policy — scoped, not AdministratorAccess --------------------
PERMISSIONS_POLICY=$(cat <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "TerraformState",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::scansettle-terraform-state-*",
        "arn:aws:s3:::scansettle-terraform-state-*/*"
      ]
    },
    {
      "Sid": "TerraformLocks",
      "Effect": "Allow",
      "Action": ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem"],
      "Resource": "arn:aws:dynamodb:*:*:table/scansettle-terraform-locks"
    },
    {
      "Sid": "Ec2AndVpc",
      "Effect": "Allow",
      "Action": [
        "ec2:Describe*",
        "ec2:RunInstances", "ec2:TerminateInstances", "ec2:StopInstances", "ec2:StartInstances",
        "ec2:CreateTags", "ec2:DeleteTags",
        "ec2:CreateSecurityGroup", "ec2:DeleteSecurityGroup",
        "ec2:AuthorizeSecurityGroupIngress", "ec2:AuthorizeSecurityGroupEgress",
        "ec2:RevokeSecurityGroupIngress", "ec2:RevokeSecurityGroupEgress",
        "ec2:AllocateAddress", "ec2:ReleaseAddress", "ec2:AssociateAddress", "ec2:DisassociateAddress",
        "ec2:CreateKeyPair", "ec2:DeleteKeyPair"
      ],
      "Resource": "*"
    },
    {
      "Sid": "IamForInstanceRole",
      "Effect": "Allow",
      "Action": [
        "iam:CreateRole", "iam:DeleteRole", "iam:GetRole",
        "iam:CreateInstanceProfile", "iam:DeleteInstanceProfile", "iam:GetInstanceProfile",
        "iam:AddRoleToInstanceProfile", "iam:RemoveRoleFromInstanceProfile",
        "iam:AttachRolePolicy", "iam:DetachRolePolicy", "iam:PutRolePolicy", "iam:DeleteRolePolicy", "iam:GetRolePolicy",
        "iam:PassRole", "iam:TagRole", "iam:ListRolePolicies", "iam:ListAttachedRolePolicies"
      ],
      "Resource": [
        "arn:aws:iam::*:role/scansettle-*",
        "arn:aws:iam::*:instance-profile/scansettle-*"
      ]
    },
    {
      "Sid": "SsmForSecretsAndDeploy",
      "Effect": "Allow",
      "Action": [
        "ssm:PutParameter", "ssm:GetParameter", "ssm:GetParameters", "ssm:DeleteParameter",
        "ssm:AddTagsToResource", "ssm:SendCommand", "ssm:GetCommandInvocation", "ssm:ListCommandInvocations"
      ],
      "Resource": "*"
    },
    {
      "Sid": "Ecr",
      "Effect": "Allow",
      "Action": [
        "ecr:CreateRepository", "ecr:DeleteRepository", "ecr:DescribeRepositories",
        "ecr:PutLifecyclePolicy", "ecr:SetRepositoryPolicy", "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage",
        "ecr:PutImage", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload",
        "ecr:TagResource"
      ],
      "Resource": "*"
    },
    {
      "Sid": "KmsLookup",
      "Effect": "Allow",
      "Action": ["kms:ListAliases", "kms:DescribeKey"],
      "Resource": "*"
    }
  ]
}
EOF
)

aws iam put-role-policy \
  --role-name "${ROLE_NAME}" \
  --policy-name "scansettle-nonprod-deploy-policy" \
  --policy-document "${PERMISSIONS_POLICY}"

ROLE_ARN=$(aws iam get-role --role-name "${ROLE_NAME}" --query 'Role.Arn' --output text)

echo ""
echo "=================================================================="
echo "Bootstrap complete."
echo ""
echo "1. Add this as a REPOSITORY SECRET (Settings -> Secrets and variables"
echo "   -> Actions -> Secrets tab -> New repository secret):"
echo "   github.com/${GITHUB_REPO}/settings/secrets/actions"
echo ""
echo "     AWS_DEPLOY_ROLE_ARN = ${ROLE_ARN}"
echo ""
echo "2. Add these as REPOSITORY VARIABLES (same page -> Variables tab ->"
echo "   New repository variable):"
echo "   github.com/${GITHUB_REPO}/settings/variables/actions"
echo ""
echo "     AWS_REGION      = ${REGION}"
echo "     TF_STATE_BUCKET = ${STATE_BUCKET}"
echo "     TF_LOCK_TABLE   = ${LOCK_TABLE}"
echo ""
echo "The role ARN is a secret (workflows reference it as \${{ secrets.AWS_DEPLOY_ROLE_ARN }});"
echo "the other three are non-sensitive config (referenced as \${{ vars.* }})."
echo "=================================================================="
