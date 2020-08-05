#!/usr/bin/env sh

mvn -f pom.xml clean install -DskipTests --non-recursive
mvn -f sfs-ms-chat-gateway-client/pom.xml clean install -DskipTests
mvn -f sfs-ms-chat-gateway-dal/pom.xml clean install -DskipTests
