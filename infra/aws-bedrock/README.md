# Amazon Bedrock learning activity

The AWS Free Tier activity requires one foundation-model response. The request
uses the active `EU Nova Micro` inference profile, asks for at most 20 tokens,
and sends no application or personal data.

```powershell
aws bedrock-runtime converse `
  --cli-input-json file://request.json `
  --profile vadym-work `
  --region eu-central-1
```

After the response, check **Billing and Cost Management → Credits** every five
minutes for up to 30 minutes. The expected row is
`Explore AWS: Use a foundational model in the Amazon Bedrock playground` for
`$20.00`.

The request creates no persistent Bedrock resource, so there is nothing to
destroy afterward. Never join AWS Organizations and never run Control Tower
while earning activity credits.
