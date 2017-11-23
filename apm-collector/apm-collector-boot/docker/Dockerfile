FROM openjdk:8u111-jdk

ENV ZK_ADDRESSES=127.0.0.1:2181 \
    ES_CLUSTER_NAME=CollectorDBCluster \
    ES_ADDRESSES=localhost:9300 \
    BIND_HOST=localhost  \
    NAMING_BIND_HOST=localhost \
    NAMING_BIND_PORT=10800 \
    REMOTE_BIND_PORT=11800 \
    AGENT_GRPC_BIND_PORT=11800 \
    AGENT_JETTY_BIND_HOST=localhost \
    AGENT_JETTY_BIND_PORT=12800 \
    UI_JETTY_BIND_PORT=12800 \
    UI_JETTY_BIND_HOST=localhost

ADD skywalking-collector.tar.gz /usr/local
COPY collectorService.sh /usr/local/skywalking-collector/bin
COPY log4j2.xml /usr/local/skywalking-collector/config
COPY application.yml /usr/local/skywalking-collector/config
COPY docker-entrypoint.sh /

RUN chmod +x /usr/local/skywalking-collector/bin/collectorService.sh && chmod +x /docker-entrypoint.sh

EXPOSE 10800
EXPOSE 11800
EXPOSE 12800

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/usr/local/skywalking-collector/bin/collectorService.sh"]
