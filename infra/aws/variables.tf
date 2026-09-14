variable "aws_region" {
  description = "AWS region used for the temporary deployment."
  type        = string
  default     = "eu-central-1"
}

variable "aws_profile" {
  description = "Local AWS CLI profile used by Terraform."
  type        = string
  default     = "vadym-work"
}

variable "project_name" {
  description = "Prefix used for resource names and tags."
  type        = string
  default     = "car-sharing-demo"
}

variable "instance_type" {
  description = "EC2 instance type. t3.small has enough memory to build the Java image and is intended to run only briefly."
  type        = string
  default     = "t3.small"
}

variable "repository_url" {
  description = "Public Git repository cloned by the EC2 bootstrap script."
  type        = string
  default     = "https://github.com/vDmytriv01/car-sharing-service.git"
}

variable "repository_branch" {
  description = "Remote branch containing the immutable deployment commit."
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

variable "app_port" {
  description = "Public port exposed by the demo API."
  type        = number
  default     = 8080
}

variable "allowed_cidr" {
  description = "Single public IPv4 address allowed to access the demo API, expressed as a /32 CIDR."
  type        = string
  default     = "127.0.0.1/32"

  validation {
    condition     = can(cidrnetmask(var.allowed_cidr)) && endswith(var.allowed_cidr, "/32")
    error_message = "allowed_cidr must be a valid single-address /32 CIDR."
  }
}

variable "auto_terminate_minutes" {
  description = "Failsafe lifetime for the billable EC2 instance."
  type        = number
  default     = 60

  validation {
    condition     = var.auto_terminate_minutes >= 30 && var.auto_terminate_minutes <= 120
    error_message = "auto_terminate_minutes must be between 30 and 120 minutes."
  }
}
