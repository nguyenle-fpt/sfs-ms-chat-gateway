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
    echo "Starting backend..."

    if [ "$PARAMSTORE" = "1" ] || [ "$(echo $PARAMSTORE | tr '[:upper:]' '[:lower:]')" = "true" ]
    then
        java -jar /opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.jar
    else
        java -jar /opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.jar --spring.config.location=classpath:application.yaml,file:/opt/symphony/sfs-ms-chat-gateway/sfs-ms-chat-gateway.yaml
    fi
}

checkJar
checkYaml
start
