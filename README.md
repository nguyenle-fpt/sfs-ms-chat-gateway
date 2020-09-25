# sfs-ms-chat-gateway

## Subscribe to the forwarder topic
One SNS topic per environment named `sym-s2-${ENVIRONMENT}-master-s2fwd-input` receives all the forwarder events, for all forwarder-enabled pods in the environment.

Create a SQS queue
```
aws sqs create-queue --queue-name sfs-dev-federation-events --tags "Name=sfs-dev-federation-events,Owner=CES,Org=Engineering,Customer=Symphony"

SQS_POLICY='{"Policy":"{\"Version\":\"2012-10-17\",\"Id\":\"<QUEUE_ARN>/SQSDefaultPolicy\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"SQS:SendMessage\",\"Resource\":\"<QUEUE_ARN>\",\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"<SNS_TOPIC_ARN>\"}}}]}"}'
aws sqs set-queue-attributes --queue-url <QUEUE_URL> --attributes $SQS_POLICY
```

Subscribe the SQS queue to the SNS topic
```
POD_ID=196
aws sns subscribe --topic-arn <SNS_TOPIC_ARN> --protocol sqs --notification-endpoint <SQS_QUEUE_ARN> --attributes '{"RawMessageDelivery": "false", "FilterPolicy": "{\"payloadType\": [\"com.symphony.s2.model.chat.SocialMessage\",\"com.symphony.s2.model.chat.MaestroMessage\"], \"podId\": [{\"numeric\": [\"=\",'$POD_ID'.0]}]}"}'
```

Test that the queue receive data:
```
aws sqs receive-message --queue-url <QUEUE_URL> --wait-time-seconds 20  
```

## Create a local forwarder topic

aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name sfs-federation-events


## start sfs-ms-chat-gateway
```
SPRING_PROFILES_ACTIVE=local mvn -f sfs-ms-chat-gateway-server/pom.xml clean spring-boot:run
```
