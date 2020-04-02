# sfs-ms-chat-gateway

## Subscribe to the forwarder topic
One SNS topic per environment named `sym-s2-${ENVIRONMENT}-master-s2fwd-input` receives all the forwarder events, for all forwarder-enabled pods in the environment.

Create a SQS queue
```
aws sqs create-queue --queue-name sfs-dev-federation-events --tags "Key=Name,Value=sfs-dev-federation-events Key=Owner,Value=CES Key=Org,Value=Engineering Key=Customer,Value=Symphony"

SQS_POLICY='{ "Policy": "{ \"Version\": \"2012-10-17\", \"Id\": \"<QUEUE_ARN>/SQSDefaultPolicy\", \"Statement\": [ { \"Effect\": \"Allow\", \"Principal\": \"*\", \"Action\": \"SQS:SendMessage\", \"Resource\": \"<QUEUE_ARN>\", \"Condition\": { \"ArnEquals\": { \"aws:SourceArn\": \"<SNS_TOPIC_ARN>\" } } } ] }" }'
aws sqs set-queue-attributes --queue-url <QUEUE_URL> --attributes $SQS_POLICY
```

Subscribe the SQS queue to the SNS topic
```
POD_ID=198
aws sns subscribe --topic-arn <SNS_TOPIC_ARN> --protocol sqs --notification-endpoint <SQS_QUEUE_ARN> --attributes '{"RawMessageDelivery": "true", "FilterPolicy": "{\"payloadType\": [\"com.symphony.s2.model.chat.SocialMessage\"], \"podId\": [{\"numeric\": [\"=\",'$POD_ID'.0]}]}"}'
```

Test that the queue receive data:
```
aws sqs receive-message --queue-url <QUEUE_URL> --wait-time-seconds 20  
```

## Create a local forwarder topic

aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name sfs-federation-events
