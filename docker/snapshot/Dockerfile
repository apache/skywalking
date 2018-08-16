FROM openjdk:8-jre-alpine

ENV DIST_NAME=apache-skywalking-apm-incubating \
    JAVA_OPTS=" -Xms256M -Xmx512M"

# Install required packages
RUN apk add --no-cache \
    bash

COPY "$DIST_NAME.tar.gz" /

RUN set -ex; \
    tar -xzf "$DIST_NAME.tar.gz"; \
    rm -rf "$DIST_NAME.tar.gz"; rm -rf config/log4j2.xml;

WORKDIR $DIST_NAME

COPY log4j2.xml config/
COPY docker-entrypoint.sh .

EXPOSE 12800 11800

ENTRYPOINT ["./docker-entrypoint.sh"]