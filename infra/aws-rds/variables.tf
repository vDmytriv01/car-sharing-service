variable "aws_region" {
  description = "AWS region used for the temporary RDS activity."
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
  default     = "car-sharing-rds-activity"
}
