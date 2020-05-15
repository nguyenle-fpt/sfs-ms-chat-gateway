#!/usr/bin/env sh
export JAVA_PROGRAM_ARGS=`echo "$@"`
mvn -q clean compile exec:java \
  -Dexec.mainClass="com.symphony.sfs.ms.chat.InitSfsChatGatewayDB" \
  -Dexec.args="$JAVA_PROGRAM_ARGS"
