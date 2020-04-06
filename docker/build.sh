#!/bin/bash

ENV=${1:-dev}
SERVICE_NAME=sfs-${ENV}-ms-chat-gateway

POM_VERSION=$(sed -n -e 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)
VERSION=$(echo ${POM_VERSION} | cut -d '-' -f 1)
BUILD_NUMBER=$(date +"%Y%m%d%H%M%S")
TAG=${2:-"${VERSION}-${BUILD_NUMBER}"}

IMAGE_NAME="${SERVICE_NAME}/${SERVICE_NAME}"
FINAL_IMAGE_VERSION="${IMAGE_NAME}:${TAG}"

function mvnBuild() {
  mvn clean package -DskipTests -f pom.xml
}

function prepareFiles() {
    echo "Preparing files..."
    mkdir -p docker/resources
    # mkdir -p docker/resources/templates
    mkdir -p docker/resources/certs
    cp "target/sfs-ms-chat-gateway-${POM_VERSION}.jar" docker/resources/sfs-ms-chat-gateway.jar
    cp "src/main/resources/application-${ENV}.yaml" docker/resources/sfs-ms-chat-gateway.yaml
    cp docker/startup.sh docker/resources/startup.sh
    # cp -R misc/templates/* docker/resources/templates
    cp -R misc/certs/* docker/resources/certs
}

function createEcrRepo() {
    echo "Creating AWS ECR Repository..."
    aws ecr describe-repositories --repository-names ${SERVICE_NAME} || aws ecr create-repository --repository-name ${SERVICE_NAME}
}

function buildContainer() {
    echo "Building Container..."
    $(aws ecr get-login --no-include-email)
    docker build --rm --build-arg profile="${ENV}" --file ./docker/Dockerfile --tag "${FINAL_IMAGE_VERSION}" .
    echo "Container built successfully!"
}

function uploadToEcr() {
    echo "Uploading Image ${FINAL_IMAGE_VERSION} to ECR..."
    AWS_ID=$(aws sts get-caller-identity --output text --query 'Account')
    AWS_REGION=$(aws configure get region)
    echo "Tagging Build..."
    docker tag "${FINAL_IMAGE_VERSION}" "${AWS_ID}".dkr.ecr."${AWS_REGION}".amazonaws.com/"${SERVICE_NAME}"
    echo "Pushing to ECR..."
    IMAGE_NAME="${AWS_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${SERVICE_NAME}"
    docker push ${IMAGE_NAME}
    echo "Pushed ${IMAGE_NAME}!"
}

function reTagLatest() {
    echo "Grab Manifest..."
    MANIFEST=$(aws ecr batch-get-image --repository-name "${SERVICE_NAME}" --image-ids imageTag=latest --query 'images[].imageManifest' --output text)
    echo "Re-Tagging Image on ECR..."
    aws ecr put-image --repository-name "${SERVICE_NAME}" --image-tag "${TAG}" --image-manifest "${MANIFEST}" --region "${AWS_REGION}"
    echo "Re-Tagging Finished! tag=${TAG}"
}

mvnBuild
prepareFiles
createEcrRepo
buildContainer
uploadToEcr
reTagLatest
