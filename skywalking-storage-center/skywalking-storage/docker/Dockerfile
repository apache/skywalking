FROM openjdk:8u111-jdk

# grab gosu for easy step-down from root
ENV GOSU_VERSION 1.7
RUN set -x \
	&& wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$(dpkg --print-architecture)" \
	&& wget -O /usr/local/bin/gosu.asc "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$(dpkg --print-architecture).asc" \
	&& export GNUPGHOME="$(mktemp -d)" \
	&& gpg --keyserver ha.pool.sks-keyservers.net --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 \
	&& gpg --batch --verify /usr/local/bin/gosu.asc /usr/local/bin/gosu \
	&& rm -r "$GNUPGHOME" /usr/local/bin/gosu.asc \
	&& chmod +x /usr/local/bin/gosu \
	&& gosu nobody true

RUN useradd -r elasticsearch

ENV SERVER_PORT=34000 \
    REGISTRY_CENTER_URL=localhost:2181

ADD skywalking-storage.tar /home/elasticsearch
COPY config.properties /home/elasticsearch/skywalking-storage/config
COPY docker-entrypoint.sh /home/elasticsearch/skywalking-storage/bin
COPY storageServer.sh /home/elasticsearch/skywalking-storage/bin

RUN chmod +x  /home/elasticsearch/skywalking-storage/bin/docker-entrypoint.sh && chmod +x /home/elasticsearch/skywalking-storage/bin/storageServer.sh && mkdir -p /home/elasticsearch/skywalking-storage/data/file && chown -R elasticsearch:elasticsearch /home/elasticsearch

VOLUME ["/home/elasticsearch/skywalking-storage/data/file", "/home/elasticsearch/skywalking-storage/data/index/data"]

EXPOSE 34000 8000

ENTRYPOINT ["/home/elasticsearch/skywalking-storage/bin/docker-entrypoint.sh"]
CMD ["/home/elasticsearch/skywalking-storage/bin/storageServer.sh"]
