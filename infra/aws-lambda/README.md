# Temporary Lambda web-app activity

This module creates a minimal Python Lambda web app with a Function URL for
the AWS Free Tier learning activity. The URL is temporarily public because the
activity requires a browser-accessible web app. The function returns static
HTML and has no access to other AWS services. The URL is kept only until the
activity credit appears, then the entire stack is destroyed.

```powershell
..\..\.tools\terraform\terraform.exe init
..\..\.tools\terraform\terraform.exe plan -input=false -out=".terraform/lambda-activity.tfplan"
..\..\.tools\terraform\terraform.exe apply -input=false ".terraform/lambda-activity.tfplan"
```

Open the `function_url` output once and confirm HTTP 200. Check **Billing and
Cost Management → Credits** every five minutes for up to 30 minutes. The
expected row is `Explore AWS: Create a web app using AWS Lambda` for `$20.00`.

Destroy the module immediately after the credit appears:

```powershell
..\..\.tools\terraform\terraform.exe destroy -auto-approve -input=false
..\..\.tools\terraform\terraform.exe state list
aws lambda get-function --function-name car-sharing-lambda-activity `
  --profile vadym-work --region eu-central-1
```

The Terraform state must be empty and AWS must return `ResourceNotFoundException`.
Never join AWS Organizations and never run Control Tower while earning credits.
