#!/bin/bash

set -e

CLASSPATH="config:$CLASSPATH"
for i in oap-libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

java ${JAVA_OPTS} -classpath $CLASSPATH \
 -Dstorage.elasticsearch.clusterNodes=elasticsearch:9200 \
 org.apache.skywalking.oap.server.starter.OAPServerStartUp
