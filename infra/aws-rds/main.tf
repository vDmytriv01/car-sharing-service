data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "random_password" "master" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "activity" {
  name       = var.project_name
  subnet_ids = data.aws_subnets.default.ids

  tags = {
    Name = var.project_name
  }
}

resource "aws_security_group" "database" {
  name_prefix = "${var.project_name}-"
  description = "No-ingress security group for the temporary RDS activity"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project_name}-database"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "activity" {
  identifier = var.project_name

  engine         = "mysql"
  engine_version = "8.4.9"
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  username = "activityadmin"
  password = random_password.master.result
  port     = 3306

  db_subnet_group_name   = aws_db_subnet_group.activity.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period      = 0
  deletion_protection          = false
  skip_final_snapshot          = true
  delete_automated_backups     = true
  apply_immediately            = true
  auto_minor_version_upgrade   = true
  performance_insights_enabled = false
  monitoring_interval          = 0

  tags = {
    Name = var.project_name
  }
}

resource "time_static" "cleanup_deadline" {
  triggers = {
    db_resource_id = aws_db_instance.activity.resource_id
  }
}

data "aws_iam_policy_document" "scheduler_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "cleanup" {
  name_prefix        = "${var.project_name}-cleanup-"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume_role.json
}

data "aws_iam_policy_document" "cleanup" {
  statement {
    actions   = ["rds:DeleteDBInstance"]
    resources = [aws_db_instance.activity.arn]
  }
}

resource "aws_iam_role_policy" "cleanup" {
  name_prefix = "delete-temporary-rds-"
  role        = aws_iam_role.cleanup.id
  policy      = data.aws_iam_policy_document.cleanup.json
}

resource "aws_scheduler_schedule" "cleanup" {
  name_prefix                  = "${var.project_name}-cleanup-"
  description                  = "Failsafe deletion of the temporary RDS learning database"
  schedule_expression          = "at(${formatdate("YYYY-MM-DD'T'hh:mm:ss", timeadd(time_static.cleanup_deadline.rfc3339, "60m"))})"
  schedule_expression_timezone = "UTC"
  action_after_completion      = "DELETE"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:rds:deleteDBInstance"
    role_arn = aws_iam_role.cleanup.arn

    input = jsonencode({
      DBInstanceIdentifier   = aws_db_instance.activity.identifier
      DeleteAutomatedBackups = true
      SkipFinalSnapshot      = true
    })

    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 3
    }
  }

  depends_on = [aws_iam_role_policy.cleanup]
}
