#!/bin/bash

# replace all variables
echo "replace mysql-server with $MYSQL_URL"
eval sed -i -e 's/\{mysql-server\}/$MYSQL_URL/' /usr/local/tomcat/webapps/skywalking/WEB-INF/classes/jdbc.properties

echo "replace mysql-username with $MYSQL_USER"
eval sed -i -e 's/\{mysql-username\}/$MYSQL_USER/' /usr/local/tomcat/webapps/skywalking/WEB-INF/classes/jdbc.properties

echo "replace mysql-password with $MYSQL_PASSWORD"
eval sed -i -e 's/\{mysql-password\}/$MYSQL_PASSWORD/' /usr/local/tomcat/webapps/skywalking/WEB-INF/classes/jdbc.properties

echo "replace registry_center_url with $REGISTRY_CENTER_URL"
eval sed -i -e 's/\{registry_center_url\}/$REGISTRY_CENTER_URL/' /usr/local/tomcat/webapps/skywalking/WEB-INF/classes/config.properties

exec "$@"