FROM openjdk:8-jdk

ADD skywalking-web.tar.gz /usr/local
COPY startup.sh /usr/local/skywalking-web/bin
CMD ["/bin/bash","/usr/local/skywalking-web/bin/startup.sh"]
