# Temporary AWS deployment

This Terraform stack launches a short-lived EC2 demo for the AWS Free Tier
learning activity. It creates one VPC, one public subnet, an internet gateway,
a security group, an EC2 instance, and an EC2 role for Session Manager. No SSH
port or SSH key is created.

The instance clones the public repository, builds the application image, and
runs the API and MySQL in Docker. Stripe and Telegram use non-functional demo
placeholders; do not exercise those integrations in this temporary deployment.

## Cost safety

EC2 compute, EBS storage, and the public IPv4 address can consume AWS credits.
Keep the deployment only long enough to verify the activity credit, then run
`terraform destroy`. The root EBS volume is encrypted and deleted with the
instance. Terraform state and variable files are ignored by Git.

## Commands

From `infra/aws`, using the project-local Terraform binary on Windows:

```powershell
..\..\.tools\terraform\terraform.exe init
..\..\.tools\terraform\terraform.exe plan
..\..\.tools\terraform\terraform.exe apply
..\..\.tools\terraform\terraform.exe output -raw health_url
..\..\.tools\terraform\terraform.exe destroy
```

The stack uses the local `vadym-work` AWS CLI profile by default. Override the
`aws_profile` variable if you use another local profile name.
