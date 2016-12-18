FROM openjdk:8u111-jdk

ENV SERVER_PORT=23000 \
    REGISTRY_CENTER_URL=localhost:2181 \
    ALARM_REDIS_SERVER=localhost:6379

ADD skywalking-routing.tar /usr/local/
COPY config.properties /usr/local/skywalking-routing/config/
COPY docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

EXPOSE 23000

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/usr/local/skywalking-routing/bin/routingServer.sh"]
