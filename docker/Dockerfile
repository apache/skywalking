FROM openjdk:8-jdk

ENV COLLECTOR_SERVERS=127.0.0.1:12800

ADD skywalking-web.tar.gz /usr/local
COPY startup.sh /usr/local/skywalking-web/bin

ADD docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh && chmod +x /usr/local/skywalking-web/bin/startup.sh

ENTRYPOINT [ "/docker-entrypoint.sh" ]
CMD ["/usr/local/skywalking-web/bin/startup.sh"]
