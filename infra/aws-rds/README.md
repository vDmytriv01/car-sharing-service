# Temporary RDS learning activity

This module creates one private, single-AZ MySQL RDS database for the AWS
Free Tier learning activity. It uses the smallest configured instance class,
20 GB of encrypted `gp3` storage, no public access, no backups, no enhanced
monitoring, and no Performance Insights. The AWS provider refuses to operate
outside account `852064978823`.

The database is intentionally not connected to the application. Keep it only
until the `Explore AWS: Create an Amazon RDS database` credit appears, then
destroy it immediately. An EventBridge Scheduler failsafe deletes the billable
database after 60 minutes if the local session is interrupted; the remaining
non-billable Terraform resources can then be removed with `terraform destroy`.

```powershell
Copy-Item terraform.tfvars.example terraform.tfvars
..\..\.tools\terraform\terraform.exe init
..\..\.tools\terraform\terraform.exe plan -input=false -out=".terraform/rds-activity.tfplan"
..\..\.tools\terraform\terraform.exe apply -input=false ".terraform/rds-activity.tfplan"
```

Review the saved plan before applying it. It must target account
`852064978823`, create one `db.t4g.micro` MySQL database, and contain no
Organizations or Control Tower resources.

After AWS credits the activity:

```powershell
..\..\.tools\terraform\terraform.exe destroy -auto-approve -input=false
..\..\.tools\terraform\terraform.exe state list
```

Check **Billing and Cost Management → Credits** every five minutes for up to
30 minutes. The expected row is `Explore AWS: Create an Amazon RDS database`
for `$20.00`.

The final `state list` output must be empty. Also verify the remote database is
gone instead of trusting local state alone:

```powershell
aws rds describe-db-instances `
  --db-instance-identifier car-sharing-rds-activity `
  --profile vadym-work `
  --region eu-central-1
```

The expected result is `DBInstanceNotFound`. Never join the account to AWS
Organizations and never run Control Tower while earning activity credits.
