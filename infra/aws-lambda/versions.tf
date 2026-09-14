terraform {
  required_version = ">= 1.10.0"

  required_providers {
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  profile = var.aws_profile
  region  = var.aws_region

  allowed_account_ids = ["852064978823"]

  default_tags {
    tags = {
      ManagedBy = "Terraform"
      Project   = var.project_name
      Purpose   = "AWS Free Tier Lambda learning activity"
    }
  }
}
