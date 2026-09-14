output "db_identifier" {
  description = "Identifier of the temporary RDS database."
  value       = aws_db_instance.activity.identifier
}

output "db_status" {
  description = "Current RDS database status recorded by Terraform."
  value       = aws_db_instance.activity.status
}

output "failsafe_cleanup_at" {
  description = "UTC time when AWS Scheduler will delete the RDS database if manual cleanup has not run."
  value       = timeadd(time_static.cleanup_deadline.rfc3339, "60m")
}
