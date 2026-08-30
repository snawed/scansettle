terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Bucket/key/region/dynamodb_table are intentionally omitted here — passed at
  # `terraform init` time via -backend-config (see .github/workflows/terraform-nonprod.yml),
  # so this same config works whichever AWS account/bucket bootstrap.sh created.
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "scansettle"
      Environment = "nonprod"
      ManagedBy   = "terraform"
    }
  }
}
