#!/bin/bash

echo "replace ALARM_COORDINATE_ZK_ADDRESS with $ALARM_COORDINATE_ZK_ADDRESS"
eval sed -i -e 's/\{ALARM_COORDINATE_ZK_ADDRESS\}/$ALARM_COORDINATE_ZK_ADDRESS/' /usr/local/skywalking-alarm/config/config.properties

echo "replace MYSQL_SERVER with $MYSQL_SERVER"
eval sed -i -e 's/\{MYSQL_SERVER\}/$MYSQL_SERVER/' /usr/local/skywalking-alarm/config/config.properties

echo "replace MYSQL_USER with $MYSQL_USER"
eval sed -i -e 's/\{MYSQL_USER\}/$MYSQL_USER/' /usr/local/skywalking-alarm/config/config.properties

echo "replace MYSQL_PASSWORD with $MYSQL_PASSWORD"
eval sed -i -e 's/\{MYSQL_PASSWORD\}/$MYSQL_PASSWORD/' /usr/local/skywalking-alarm/config/config.properties

echo "replace REDIS_SERVER with $REDIS_SERVER"
eval sed -i -e 's/\{REDIS_SERVER\}/$REDIS_SERVER/' /usr/local/skywalking-alarm/config/config.properties


echo "replcae ALARM_MAIL_HOST with $ALARM_MAIL_HOST"
eval sed -i -e 's/\{ALARM_MAIL_HOST\}/$ALARM_MAIL_HOST/'  /usr/local/skywalking-alarm/config/config.properties

echo "replace MAIL_SSL_ENABLE with $MAIL_SSL_ENABLE"
eval sed -i -e 's/\{MAIL_SSL_ENABLE\}/$MAIL_SSL_ENABLE/'   /usr/local/skywalking-alarm/config/config.properties

echo "replace MAIL_USER_NAME with $MAIL_USER_NAME"
eval sed -i -e 's/\{MAIL_USER_NAME\}/$MAIL_USER_NAME/'    /usr/local/skywalking-alarm/config/config.properties

echo "replace MAIL_PASSWORD with $MAIL_PASSWORD"
eval sed -i -e 's/\{MAIL_PASSWORD\}/$MAIL_PASSWORD/'  /usr/local/skywalking-alarm/config/config.properties

echo "replace MAIL_SENDER_MAIL with $MAIL_SENDER_MAIL"
eval sed -i -e 's/\{MAIL_SENDER_MAIL\}/$MAIL_SENDER_MAIL/' /usr/local/skywalking-alarm/config/config.properties

echo "replace WEBUI_DEPLOY_ADDRESS with $WEBUI_DEPLOY_ADDRESS"
eval sed -i -e 's/\{WEBUI_DEPLOY_ADDRESS\}/$WEBUI_DEPLOY_ADDRESS/' /usr/local/skywalking-alarm/config/config.properties

echo "replace WEBUI_CONTEXT_NAME with $WEBUI_CONTEXT_NAME"
eval sed -i -e 's/\{WEBUI_CONTEXT_NAME\}/$WEBUI_CONTEXT_NAME/' /usr/local/skywalking-alarm/config/config.properties

mkdir -p /usr/local/skywalking-alarm/logs

exec "$@"
