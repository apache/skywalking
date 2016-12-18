FROM tomcat:8.0.39-jre8

ENV JPDA_ADDRESS="8000" \
    JPDA_TRANSPORT="dt_socket" \
    MYSQL_URL=localhost:3306 \
    MYSQL_USER=root \
    MYSQL_PASSWORD=root \
    REGISTRY_CENTER_URL=localhost:2181 \
    JDBC_INIT_DB=true

COPY skywalking /usr/local/tomcat/webapps/skywalking
COPY classes /usr/local/tomcat/webapps/skywalking/WEB-INF/classes/
COPY jdbc.properties /usr/local/tomcat/webapps/skywalking/WEB-INF/classes/
COPY config.properties /usr/local/tomcat/webapps/skywalking/WEB-INF/classes/
COPY docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["catalina.sh", "jpda", "run"]