output "account_id" {
  description = "AWS account used by the deployment."
  value       = data.aws_caller_identity.current.account_id
}

output "instance_id" {
  description = "EC2 instance running the portfolio application."
  value       = aws_instance.app.id
}

output "public_url" {
  description = "Public HTTPS base URL."
  value       = "https://${local.public_hostname}"

  depends_on = [terraform_data.app_health]
}

output "health_url" {
  description = "Spring Boot health endpoint."
  value       = "https://${local.public_hostname}/actuator/health"

  depends_on = [terraform_data.app_health]
}

output "swagger_url" {
  description = "Public Swagger UI URL."
  value       = "https://${local.public_hostname}/swagger-ui.html"

  depends_on = [terraform_data.app_health]
}
