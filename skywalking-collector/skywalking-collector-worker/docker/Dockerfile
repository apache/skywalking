FROM openjdk:8-jdk

ADD skywalking-collector.tar.gz /usr/local
COPY startup.sh /usr/local/skywalking-collector/bin
CMD ["/bin/bash","/usr/local/skywalking-collector/bin/startup.sh"]
