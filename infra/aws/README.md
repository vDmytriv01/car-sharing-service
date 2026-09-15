# AWS portfolio deployment

This Terraform stack deploys the complete car-sharing service to one EC2
instance in `eu-central-1`. The instance runs Spring Boot, MySQL, and Caddy in
Docker. Caddy exposes the API through HTTPS using a stable Elastic IP and an
`sslip.io` hostname, so no domain purchase is required.

The stack creates a dedicated VPC, public subnet, internet gateway, security
group, Elastic IP, EC2 instance, instance role, and encrypted SSM parameters.
There is no SSH port or SSH key; operational access is available through AWS
Systems Manager Session Manager.

## Cost safety

EC2, EBS, Parameter Store API usage, and the public IPv4 address can consume
AWS credits. The default `t3.small` instance automatically terminates after
three days. Set `auto_terminate_minutes` to a shorter value when a brief demo is
enough, and run `terraform destroy` when the public environment is no longer
needed. The deployment does not require upgrading an AWS Free Plan account.

The HTTPS endpoint is public because it is intended for portfolio review.
Application authorization still protects manager and customer operations.
MySQL and the Spring Boot container are not exposed directly to the internet.

## Prerequisites

- AWS CLI profile `vadym-work` authenticated with `aws login`
- Terraform initialized in this directory
- the deployment commit pushed to the public `aws-deployment` branch
- a local root `.env` containing the Stripe, Telegram, and manager bootstrap
  values listed in `.env.sample`

## Deploy

Copy `terraform.tfvars.example` to the ignored `terraform.tfvars` and replace
`repository_ref` with the full pushed commit SHA.

Export the sensitive Terraform variables from the root `.env` in the current
PowerShell session. Do not place these values in a committed file:

```powershell
$values = @{}
Get-Content ..\..\.env | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
$env:TF_VAR_stripe_secret_key = $values.STRIPE_SECRET_KEY
$env:TF_VAR_stripe_webhook_secret = $values.STRIPE_WEBHOOK_SECRET
$env:TF_VAR_telegram_bot_token = $values.TELEGRAM_BOT_TOKEN
$env:TF_VAR_telegram_chat_id = $values.TELEGRAM_CHAT_ID
$env:TF_VAR_manager_email = $values.BOOTSTRAP_MANAGER_EMAIL
$env:TF_VAR_manager_first_name = $values.BOOTSTRAP_MANAGER_FIRST_NAME
$env:TF_VAR_manager_last_name = $values.BOOTSTRAP_MANAGER_LAST_NAME
$env:TF_VAR_manager_password = $values.BOOTSTRAP_MANAGER_PASSWORD
```

Then run:

```powershell
..\..\.tools\terraform\terraform.exe init
..\..\.tools\terraform\terraform.exe plan -out deployment.tfplan
..\..\.tools\terraform\terraform.exe apply deployment.tfplan
..\..\.tools\terraform\terraform.exe output -raw swagger_url
```

Terraform waits until the public health endpoint returns `UP`. The output also
contains the base API URL and the instance ID.

## Remove

Use the same sensitive environment variables and variable file when removing
the environment:

```powershell
..\..\.tools\terraform\terraform.exe destroy
```

The EC2 root disk is encrypted and deleted with the instance. Terraform also
removes the Elastic IP, network resources, role, and SSM parameters.
