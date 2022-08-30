#!/bin/bash

function checkJar() {
    if [ ! -f /opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.jar ]; then
        echo "JAR file not found, please build one first!"
        exit 1
    else
        echo "JAR found!"
    fi
}


function checkYaml() {
    if [ ! -f /opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.yaml ]; then
        echo "YAML file not found, please provide one first!"
        exit 1
    else
        echo "YAML found!"
    fi
}

function start() {
    echo "Downloading certificate..."
    aws s3 cp "s3://$CERT_BUCKET/https-keystore.p12"  /opt/symphony/sfs-ms-chat-gateway/

    echo "Starting backend..."
    if [ "$PARAMSTORE" = "1" ] || [ "$(echo $PARAMSTORE | tr '[:upper:]' '[:lower:]')" = "true" ]
    then
        java -XX:MaxRAMPercentage=75 -Dlog4j2.formatMsgNoLookups=true -jar /opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.jar
    else
        java -XX:MaxRAMPercentage=75 -Dlog4j2.formatMsgNoLookups=true -jar /opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.jar --spring.config.location=classpath:application.yaml,file:/opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.yaml
    fi
}

checkJar
checkYaml
start
