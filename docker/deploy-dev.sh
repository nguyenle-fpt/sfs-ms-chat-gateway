#!/bin/bash

ENV="dev"
LOG_GROUP="/sfs/$ENV/ms-chat-gateway"
CLUSTER="sym-ms-devb-ause1"
SERVICE_NAME="sfs-${ENV}-ms-chat-gateway"
TARGET_GROUP_NAME="sfs-$ENV-ms-chat-gateway"
AWS_REGION=$(aws configure get region)
CPU=512
MEMORY=1024

HOST_PORT=9700
CONTAINER_PORT=9700

POM_VERSION=$(sed -n -e 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)
VERSION=$(echo ${POM_VERSION} | cut -d '-' -f 1)
BUILD_NUMBER=$(date +"%Y%m%d%H%M%S")
TAG=${1:-"${VERSION}-${BUILD_NUMBER}"}



function createTaskDefinitionRevision() {
    echo "Create task definition revision"

    AWS_ID=$(aws sts get-caller-identity --output text --query 'Account')
    AWS_REGION=$(aws configure get region)
    IMAGE_NAME="${AWS_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${SERVICE_NAME}"

    TASK_DEFINITION=$(aws ecs register-task-definition --family $SERVICE_NAME \
        --requires-compatibilities EC2 \
        --memory $MEMORY \
        --cpu $CPU \
        --tags "key=Owner,value=CES key=Org,value=Engineering key=Customer,value=Symphony key=Name,value=$SERVICE_NAME" \
        --network-mode bridge \
        --task-role-arn "ces-$ENV-whatsapp-ssm" \
        --container-definitions '[
            {
            "logConfiguration": {
              "logDriver": "awslogs",
              "options": {
              "awslogs-group": "'"$LOG_GROUP"'",
              "awslogs-region": "'"$AWS_REGION"'",
              "awslogs-stream-prefix": "sfs"
              }
            },
            "portMappings": [
              {
              "hostPort": '$HOST_PORT',
              "protocol": "tcp",
              "containerPort": '$CONTAINER_PORT'
              }
            ],

            "cpu": '"$CPU"',
            "environment": [],
            "mountPoints": [],
            "memory": '"$MEMORY"',
            "volumesFrom": [],
            "image": "'"${IMAGE_NAME}"':'"${TAG}"'",
            "name": "'"$SERVICE_NAME"'"
            }
          ]')

    echo $TASK_DEFINITION
}

function updateEcsService() {
    TASK_DEFINITION_ARN=$(echo $TASK_DEFINITION | jq -r .taskDefinition.taskDefinitionArn)
    SERVICE_ARN=$(aws ecs describe-services --cluster $CLUSTER --services $SERVICE_NAME --query 'services[0].serviceArn' --output text)
    SERVICE_STATUS=$(aws ecs describe-services --cluster $CLUSTER --services $SERVICE_ARN --query 'services[0].status' --output text)

    if [ "$SERVICE_ARN" = "None" ] || [ "$SERVICE_STATUS" != "ACTIVE" ]; then
        TARGET_GROUP_ARN=$(aws elbv2 describe-target-groups --name $TARGET_GROUP_NAME | jq -r '.TargetGroups[0].TargetGroupArn')
        aws ecs create-service --cluster $CLUSTER --service-name $SERVICE_NAME --task-definition ${TASK_DEFINITION_ARN} --role "ces-$ENV-whatsapp-ssm" --load-balancers "targetGroupArn=$TARGET_GROUP_ARN,containerName=$SERVICE_NAME,containerPort=$CONTAINER_PORT" --scheduling-strategy DAEMON --launch-type EC2 --health-check-grace-period-seconds 120
    else
        aws ecs update-service --cluster $CLUSTER --service $SERVICE_NAME --task-definition ${TASK_DEFINITION_ARN} --force-new-deployment
    fi
}

createTaskDefinitionRevision
updateEcsService
