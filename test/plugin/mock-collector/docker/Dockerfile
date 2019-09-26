FROM openjdk:8-jdk

ENV GRPC_BIND_HOST=127.0.0.1 \
    GRPC_BIND_PORT=19876

ADD skywalking-mock-collector.tar.gz /usr/local
ADD config.properties /usr/local/skywalking-mock-collector/config/config.properties
EXPOSE 12800
EXPOSE 5005

ADD docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/usr/local/skywalking-mock-collector/bin/collector-startup.sh"]
