#!/usr/bin/env sh
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

PRG="$0"
PRGDIR=$(dirname "$PRG")
[ -z "$OAP_HOME" ] && OAP_HOME=$(cd "$PRGDIR/.." > /dev/null || exit 1; pwd)

OAP_LOG_DIR=${OAP_LOG_DIR:-"${OAP_HOME}/logs"}
JAVA_OPTS="${JAVA_OPTS:-  -Xms256M -Xmx4096M}"

if [ ! -d "${OAP_HOME}/logs" ]; then
    mkdir -p "${OAP_LOG_DIR}"
fi

_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=java

if [ -z "$CLASSPATH" ]; then
  CLASSPATH="$OAP_HOME/config"
else
  CLASSPATH="$OAP_HOME/config:$CLASSPATH"
fi

for i in "$OAP_HOME"/oap-libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

OAP_OPTIONS=" -Doap.logDir=${OAP_LOG_DIR}"

eval exec "\"$_RUNJAVA\" ${JAVA_OPTS} ${OAP_OPTIONS} -classpath $CLASSPATH -Dmode=no-init org.apache.skywalking.oap.server.starter.OAPServerStartUp \
        2>${OAP_LOG_DIR}/oap.log 1> /dev/null"
