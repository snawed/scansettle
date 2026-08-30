# ScanSettle non-production environment — one EC2 instance running the same
# containers as infra/docker-compose.yml, via Docker Compose. See
# docs/deployment.md Section 1 and ADR-0012 for why this is deliberately not
# the production shape.

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Latest Amazon Linux 2023 AMI, resolved at apply time rather than hardcoded —
# avoids a stale/region-specific AMI ID going out of date silently.
data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

# The account's default SSM-managed KMS key — SecureString parameters below are
# encrypted with it; resolved by alias since the actual key ARN isn't known until
# the account's first SSM SecureString parameter is created.
data "aws_kms_alias" "ssm_default" {
  name = "alias/aws/ssm"
}

# --- Security group --------------------------------------------------------
# No port 22 — SSH access is deliberately not opened; the deploy workflow and
# any operator access go through AWS Systems Manager Session Manager instead
# (the instance role below grants that), matching docs/deployment.md's
# preference to avoid an open SSH port on the internet.
resource "aws_security_group" "app" {
  name        = "scansettle-nonprod-app"
  description = "ScanSettle non-prod — app ports only, no SSH"
  vpc_id      = data.aws_vpc.default.id

  dynamic "ingress" {
    for_each = var.app_ports
    content {
      description = "ScanSettle app port ${ingress.value}"
      from_port   = ingress.value
      to_port     = ingress.value
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
    }
  }

  egress {
    description = "All outbound (image pulls, package installs, SSM)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- IAM role for the instance itself ---------------------------------------
data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "scansettle-nonprod-instance"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

# Enables AWS Systems Manager Session Manager (shell access with no open SSH
# port and no SSH key to manage) and is also what lets the deploy workflow's
# `aws ssm send-command` reach this instance.
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "instance_permissions" {
  statement {
    sid       = "ReadAppSecrets"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = ["arn:aws:ssm:*:*:parameter/scansettle/nonprod/*"]
  }
  statement {
    # SecureString parameters are encrypted with the account's default
    # aws/ssm KMS key — decrypting them (not just reading the ciphertext)
    # needs this explicitly, it isn't implied by the ssm:GetParameter grant above.
    sid       = "DecryptAppSecrets"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm_default.target_key_arn]
  }
  statement {
    sid = "PullFromEcr"
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
    ]
    resources = ["*"]
  }
  statement {
    # The deploy workflow uploads the compose file + rendered deploy script here
    # (reusing the Terraform state bucket under a separate prefix rather than
    # provisioning a second bucket) — SSM Run Command on the instance downloads
    # them from here instead of embedding them inline in the SSM command payload,
    # which has a much smaller size limit.
    sid       = "ReadDeployArtifacts"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${var.tf_state_bucket}/deploy-artifacts/*"]
  }
}

resource "aws_iam_role_policy" "instance" {
  name   = "scansettle-nonprod-instance-permissions"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.instance_permissions.json
}

resource "aws_iam_instance_profile" "instance" {
  name = "scansettle-nonprod-instance"
  role = aws_iam_role.instance.name
}

# --- Secrets ------------------------------------------------------------------
# Generated once at first apply, stored as SecureString in SSM Parameter Store
# (encrypted at rest via the default aws/ssm KMS key — free, unlike per-secret
# Secrets Manager pricing). This is explicitly a non-prod tradeoff: the values
# also end up in Terraform state (which lives in the encrypted, access-controlled
# S3 bucket bootstrap.sh created) — acceptable here the same way dev/test already
# use fixed placeholder secrets committed to the repo; production (docs/deployment.md
# Section 2) uses Secrets Manager with rotation instead.
resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "random_id" "encryption_key" {
  byte_length = 32
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/scansettle/nonprod/app-jwt-secret"
  type  = "SecureString"
  value = random_password.jwt_secret.result
}

resource "aws_ssm_parameter" "encryption_key" {
  name  = "/scansettle/nonprod/app-encryption-key"
  type  = "SecureString"
  value = random_id.encryption_key.b64_std
}

resource "aws_ssm_parameter" "postgres_password" {
  name  = "/scansettle/nonprod/postgres-password"
  type  = "SecureString"
  value = random_password.postgres_password.result
}

resource "random_password" "postgres_password" {
  length  = 32
  special = false
}

# --- EC2 instance ---------------------------------------------------------------
resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  # Installs Docker + the Compose plugin and gets the SSM agent (pre-installed
  # on AL2023) running — the actual app deploy (pulling images, writing the
  # compose file, `docker compose up -d`) happens via the deploy workflow's
  # `aws ssm send-command`, not here, so redeploys never need a new instance.
  user_data = <<-EOF
    #!/bin/bash
    set -euo pipefail
    dnf install -y docker
    systemctl enable --now docker
    usermod -aG docker ec2-user
    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    mkdir -p /opt/scansettle
  EOF

  tags = {
    Name = "scansettle-nonprod"
  }
}

resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  # Tagged (not just an output) so the deploy workflow can look this IP up via
  # `aws ec2 describe-addresses` without needing Terraform installed/configured
  # itself — infra provisioning and app deploys are deliberately decoupled.
  tags = {
    Name = "scansettle-nonprod"
  }
}

# --- ECR repositories -------------------------------------------------------------
resource "aws_ecr_repository" "api" {
  name = "scansettle-api"
  # MUTABLE, not IMMUTABLE: a manually re-run deploy for the same commit SHA
  # needs to be able to push that tag again. Production (docs/deployment.md
  # Section 2) should use IMMUTABLE for real supply-chain-integrity reasons —
  # convenience wins here instead, deliberately.
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "web" {
  name = "scansettle-web"
  # MUTABLE, not IMMUTABLE: a manually re-run deploy for the same commit SHA
  # needs to be able to push that tag again. Production (docs/deployment.md
  # Section 2) should use IMMUTABLE for real supply-chain-integrity reasons —
  # convenience wins here instead, deliberately.
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "expire_untagged" {
  for_each   = { api = aws_ecr_repository.api.name, web = aws_ecr_repository.web.name }
  repository = each.value

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire untagged images after 7 days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 7
      }
      action = { type = "expire" }
    }]
  })
}
