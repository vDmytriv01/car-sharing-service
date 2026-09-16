data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

data "aws_caller_identity" "current" {}

resource "random_password" "mysql" {
  length  = 32
  special = false
}

resource "random_password" "mysql_root" {
  length  = 32
  special = false
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

locals {
  parameter_prefix = "/${var.project_name}/prod"
  public_hostname  = "${replace(aws_eip.app.public_ip, ".", "-")}.sslip.io"
}

resource "aws_ssm_parameter" "stripe_secret_key" {
  name  = "${local.parameter_prefix}/stripe-secret-key"
  type  = "SecureString"
  value = var.stripe_secret_key

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "stripe_webhook_secret" {
  name  = "${local.parameter_prefix}/stripe-webhook-secret"
  type  = "SecureString"
  value = var.stripe_webhook_secret

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "telegram_bot_token" {
  name  = "${local.parameter_prefix}/telegram-bot-token"
  type  = "SecureString"
  value = var.telegram_bot_token
}

resource "aws_ssm_parameter" "telegram_chat_id" {
  name  = "${local.parameter_prefix}/telegram-chat-id"
  type  = "SecureString"
  value = var.telegram_chat_id
}

resource "aws_ssm_parameter" "manager_email" {
  name  = "${local.parameter_prefix}/manager-email"
  type  = "SecureString"
  value = var.manager_email
}

resource "aws_ssm_parameter" "manager_first_name" {
  name  = "${local.parameter_prefix}/manager-first-name"
  type  = "SecureString"
  value = var.manager_first_name
}

resource "aws_ssm_parameter" "manager_last_name" {
  name  = "${local.parameter_prefix}/manager-last-name"
  type  = "SecureString"
  value = var.manager_last_name
}

resource "aws_ssm_parameter" "manager_password" {
  name  = "${local.parameter_prefix}/manager-password"
  type  = "SecureString"
  value = var.manager_password
}

resource "aws_vpc" "this" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${var.project_name}-vpc"
  }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.project_name}-igw"
  }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = "10.42.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-public"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = {
    Name = "${var.project_name}-public"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "app" {
  name        = "${var.project_name}-app"
  description = "Public HTTPS access to the car-sharing portfolio API"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "HTTP certificate challenge and redirect"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.public_access_cidr]
  }

  ingress {
    description = "HTTPS API"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.public_access_cidr]
  }

  egress {
    description = "Packages, container images, and external integrations"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-app"
  }
}

resource "aws_eip" "app" {
  domain = "vpc"

  tags = {
    Name = "${var.project_name}-app"
  }
}

resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "application_parameters" {
  name = "read-application-parameters"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ssm:GetParameter",
        "ssm:GetParameters",
      ]
      Resource = [
        aws_ssm_parameter.stripe_secret_key.arn,
        aws_ssm_parameter.stripe_webhook_secret.arn,
        aws_ssm_parameter.telegram_bot_token.arn,
        aws_ssm_parameter.telegram_chat_id.arn,
        aws_ssm_parameter.manager_email.arn,
        aws_ssm_parameter.manager_first_name.arn,
        aws_ssm_parameter.manager_last_name.arn,
        aws_ssm_parameter.manager_password.arn,
      ]
    }]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2"
  role = aws_iam_role.ec2.name
}

resource "aws_instance" "app" {
  ami                         = data.aws_ami.amazon_linux_2023.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2.name

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    auto_terminate_minutes = var.auto_terminate_minutes
    aws_region             = var.aws_region
    jwt_secret             = random_password.jwt.result
    mysql_password         = random_password.mysql.result
    mysql_root_password    = random_password.mysql_root.result
    parameter_prefix       = local.parameter_prefix
    public_hostname        = local.public_hostname
    repository_branch      = var.repository_branch
    repository_ref         = var.repository_ref
    repository_url         = var.repository_url
  })

  instance_initiated_shutdown_behavior = "terminate"
  user_data_replace_on_change          = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    delete_on_termination = true
    encrypted             = true
    volume_size           = 16
    volume_type           = "gp3"
  }

  tags = {
    Name = "${var.project_name}-app"
  }

  depends_on = [
    aws_iam_role_policy.application_parameters,
    aws_iam_role_policy_attachment.ssm,
    aws_route_table_association.public,
  ]
}

resource "aws_eip_association" "app" {
  allocation_id = aws_eip.app.id
  instance_id   = aws_instance.app.id
}

resource "terraform_data" "app_health" {
  triggers_replace = [aws_instance.app.id, aws_eip.app.id]

  provisioner "local-exec" {
    interpreter = ["PowerShell", "-NoProfile", "-Command"]
    command     = <<-EOT
      $url = 'https://${local.public_hostname}/actuator/health'
      $deadline = (Get-Date).AddMinutes(25)
      while ((Get-Date) -lt $deadline) {
        try {
          $response = Invoke-RestMethod -Uri $url -TimeoutSec 15
          if ($response.status -eq 'UP') { exit 0 }
        } catch {}
        Start-Sleep -Seconds 15
      }
      Write-Error "Application did not become healthy within 25 minutes: $url"
      exit 1
    EOT
  }

  depends_on = [aws_eip_association.app]
}
