FROM openjdk:8u111-jdk

ENV SERVER_PORT=34000 \
    REGISTRY_CENTER_URL=localhost:2181

ADD skywalking-storage.tar /usr/local/
COPY config.properties /usr/local/skywalking-storage/config
COPY docker-entrypoint.sh /

RUN chmod +x /docker-entrypoint.sh

VOLUME ["/usr/local/skywalking-storage/data/file", "/usr/local/skywalking-storage/data/index/data"]

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/usr/local/skywalking-storage/bin/storageServer.sh"]
