output "function_name" {
  description = "Name of the temporary Lambda function."
  value       = aws_lambda_function.web_app.function_name
}

output "function_url" {
  description = "Temporary public URL used to verify the learning activity."
  value       = aws_lambda_function_url.web_app.function_url
}
