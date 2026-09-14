terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.13"
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
      Purpose   = "AWS Free Tier RDS learning activity"
    }
  }
}
