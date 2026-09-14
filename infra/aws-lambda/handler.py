def lambda_handler(event, context):
    return {
        "statusCode": 200,
        "headers": {"content-type": "text/html; charset=utf-8"},
        "body": "<h1>Car Sharing Service</h1><p>AWS Lambda activity is working.</p>",
    }
