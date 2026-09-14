output "account_id" {
  description = "AWS account used by the deployment."
  value       = data.aws_caller_identity.current.account_id
}

output "instance_id" {
  description = "Temporary EC2 instance ID."
  value       = aws_instance.app.id
}

output "public_url" {
  description = "Temporary public API URL."
  value       = "http://${aws_instance.app.public_ip}:${var.app_port}"
}

output "health_url" {
  description = "Temporary Spring Boot health endpoint."
  value       = "http://${aws_instance.app.public_ip}:${var.app_port}/actuator/health"
}

data "aws_caller_identity" "current" {}
