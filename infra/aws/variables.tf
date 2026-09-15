variable "aws_region" {
  description = "AWS region used for the portfolio deployment."
  type        = string
  default     = "eu-central-1"
}

variable "aws_profile" {
  description = "Local AWS CLI profile used by Terraform."
  type        = string
  default     = "vadym-work"
}

variable "project_name" {
  description = "Prefix used for resource names, parameters, and tags."
  type        = string
  default     = "car-sharing-service"
}

variable "instance_type" {
  description = "EC2 instance type used to build and run the application and MySQL containers."
  type        = string
  default     = "t3.small"
}

variable "repository_url" {
  description = "Public Git repository cloned during EC2 bootstrap."
  type        = string
  default     = "https://github.com/vDmytriv01/car-sharing-service.git"
}

variable "repository_branch" {
  description = "Remote branch containing the deployment commit."
  type        = string
  default     = "aws-deployment"
}

variable "repository_ref" {
  description = "Immutable Git commit SHA deployed to EC2. The commit must exist on the remote."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{40}$", var.repository_ref))
    error_message = "repository_ref must be a full 40-character Git commit SHA."
  }
}

variable "public_access_cidr" {
  description = "IPv4 CIDR allowed to reach the public HTTPS API."
  type        = string
  default     = "0.0.0.0/0"

  validation {
    condition     = can(cidrnetmask(var.public_access_cidr)) && !strcontains(var.public_access_cidr, ":")
    error_message = "public_access_cidr must be a valid IPv4 CIDR."
  }
}

variable "auto_terminate_minutes" {
  description = "Cost-safety lifetime for the EC2 instance. The default keeps the demo online for three days."
  type        = number
  default     = 4320

  validation {
    condition     = var.auto_terminate_minutes >= 60 && var.auto_terminate_minutes <= 10080
    error_message = "auto_terminate_minutes must be between 60 minutes and seven days."
  }
}

variable "stripe_secret_key" {
  description = "Stripe test-mode API key stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "stripe_webhook_secret" {
  description = "Stripe webhook signing secret stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "telegram_bot_token" {
  description = "Telegram bot token stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "telegram_chat_id" {
  description = "Telegram administrator chat ID stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "manager_email" {
  description = "Initial manager email stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "manager_first_name" {
  description = "Initial manager first name stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "manager_last_name" {
  description = "Initial manager last name stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "manager_password" {
  description = "Initial manager password stored in SSM Parameter Store."
  type        = string
  sensitive   = true
}
