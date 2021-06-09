FROM 189141687483.dkr.ecr.us-east-1.amazonaws.com/devops/openjdk:11.0.11-jre

ARG profile=dev
ARG aws_paramstore_enabled=1

EXPOSE 9700

WORKDIR /opt/symphony/sfs-ms-chat-gateway

COPY docker/resources /opt/symphony/sfs-ms-chat-gateway

RUN apt-get update && apt-get install -y procps
RUN chmod 755 /opt/symphony/sfs-ms-chat-gateway/startup.sh

ENV LOGGING_PATH=./
ENV PARAMSTORE=${aws_paramstore_enabled}
ENV SPRING_PROFILES_ACTIVE=${profile}

CMD "/opt/symphony/sfs-ms-chat-gateway/startup.sh" "run"
