# Temporary AWS deployment

This Terraform stack launches a short-lived EC2 demo for the AWS Free Tier
learning activity. It creates one VPC, one public subnet, an internet gateway,
a security group, an EC2 instance, and an EC2 role for Session Manager. No SSH
port or SSH key is created.

The instance shallow-clones the configured public branch, verifies that its
HEAD is the exact required commit SHA, builds the application image, and runs
the API and MySQL in Docker. Stripe and Telegram use non-functional demo
placeholders; do not exercise those integrations in this temporary deployment.

## Cost safety

EC2 compute, EBS storage, and the public IPv4 address can consume AWS credits.
The instance terminates itself after 60 minutes by default, including when its
bootstrap fails. Keep it only long enough to verify the activity credit, then
run `terraform destroy` to remove the remaining free VPC and IAM resources. The
root EBS volume is encrypted and deleted with the instance. Terraform state and
variable files are ignored by Git.

## Commands

From `infra/aws`, using the project-local Terraform binary on Windows:

```powershell
Copy-Item terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars: set the pushed commit SHA and your public IPv4 /32.
..\..\.tools\terraform\terraform.exe init
..\..\.tools\terraform\terraform.exe plan
..\..\.tools\terraform\terraform.exe apply
..\..\.tools\terraform\terraform.exe output -raw health_url
..\..\.tools\terraform\terraform.exe destroy
```

The stack uses the local `vadym-work` AWS CLI profile by default. Override the
`aws_profile` variable if you use another local profile name. Keep the ignored
`terraform.tfvars` file until destroy completes; Terraform needs the same
required inputs when it removes the stack.

The client-side readiness check intentionally uses PowerShell because this
deployment workflow runs from Windows. The EC2 bootstrap itself runs on Amazon
Linux.

After the health check succeeds, open AWS Billing and Cost Management → Credits
and look for `Explore AWS: Launch an instance using Amazon EC2`. AWS says the
credit can take up to 30 minutes to appear. Check every 5 minutes. As soon as
the `$20.00` credit is active, run `terraform destroy`; do not wait for the
60-minute failsafe.
