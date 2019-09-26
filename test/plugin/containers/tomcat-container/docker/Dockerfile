FROM tomcat:8.5.42-jdk8-openjdk
MAINTAINER zhangxin@apache.org
WORKDIR /usr/local/skywalking-agent-scenario

COPY run.sh /
COPY catalina.sh /usr/local/tomcat/bin/

COPY skywalking-mock-collector.tar.gz /usr/local/skywalking-agent-scenario
RUN tar -xvf skywalking-mock-collector.tar.gz -C /usr/local/skywalking-agent-scenario

# RUN wget -O skywalking-mock-collector.tar.gz https://github.com/SkyAPMTest/skywalking-mock-collector/releases/download/V6.0.0-2018/skywalking-mock-collector.tar.gz && \
#    tar -xvf skywalking-mock-collector.tar.gz -C /usr/local/skywalking-agent-scenario

RUN chmod +x /usr/local/tomcat/bin/catalina.sh && chmod +x /run.sh

CMD ["/run.sh"]
