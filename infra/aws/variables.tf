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

variable "repository_ref" {
  description = "Git branch or tag deployed to EC2."
  type        = string
  default     = "main"
}

variable "app_port" {
  description = "Public port exposed by the demo API."
  type        = number
  default     = 8080
}

variable "allowed_cidr" {
  description = "CIDR allowed to access the demo API. Restrict this to your public IP when possible."
  type        = string
  default     = "0.0.0.0/0"
}
