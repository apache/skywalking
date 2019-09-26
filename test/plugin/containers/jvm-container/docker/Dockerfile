FROM openjdk:7
MAINTAINER zhangxin@apache.org

ADD docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
RUN ["/bin/bash"]
