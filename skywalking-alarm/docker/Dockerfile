FROM openjdk:8u111-jdk

ENV ALARM_COORDINATE_ZK_ADDRESS=localhost:2181 \
    MYSQL_SERVER=localhost:3306 \
    MYSQL_USER=root \
    MYSQL_PASSWORD=root \
    REDIS_SERVER=localhost:6379 \
    ALARM_MAIL_HOST=smtp.mail.com \
    MAIL_SSL_ENABLE=true \
    MAIL_USER_NAME=skywalking \
    MAIL_PASSWORD=skywalking \
    MAIL_SENDER_MAIL=skywalking@mail.com \
    WEBUI_DEPLOY_ADDRESS=localhost:8080 \
    WEBUI_CONTEXT_NAME=skywalking


ADD skywalking-alarm.tar /usr/local/
COPY config.properties /usr/local/skywalking-alarm/config
COPY sw-alarm-server.sh /usr/local/skywalking-alarm/bin
COPY docker-entrypoint.sh /

RUN chmod +x /docker-entrypoint.sh && chmod +x /usr/local/skywalking-alarm/bin/sw-alarm-server.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/usr/local/skywalking-alarm/bin/sw-alarm-server.sh"]
