FROM openjdk:8-jdk

ENV CLUSTER_CURRENT_HOST_NAME=127.0.0.1 \
    CLUSTER_CURRENT_PORT=1000 \
    CLUSTER_CURRENT_ROLES=WorkersListener \
    CLUSTER_SEED_NODES=127.0.0.1:1000,127.0.0.1:1001 \
    ES_CLUSTER_NAME=CollectorDBCluster \
    ES_CLUSTER_NODES=127.0.0.1:9300 \
    ES_INDEX_SHARDS_NUMBER=2 \
    ES_INDEX_REPLICAS_NUMBER=0 \
    HTTP_HOST_NAME=127.0.0.1 \
    HTTP_PORT=7001 \
    HTTP_CONTEXT_PATH=/

ADD skywalking-collector.tar.gz /usr/local
COPY startup.sh /usr/local/skywalking-collector/bin
COPY collector.config /usr/local/skywalking-collector/config
ADD docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh && chmod +x /usr/local/skywalking-collector/bin/startup.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/usr/local/skywalking-collector/bin/startup.sh"]
