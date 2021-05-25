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

OAP_NAME=org.apache.skywalking.oap.server.starter.OAPServerStartUp
WEB_NAME=skywalking-webapp
oapPID=`ps aux|grep $OAP_NAME|grep -v grep|awk '{print $2}'|xargs`
webPID=`ps aux|grep $WEB_NAME|grep -v grep|awk '{print $2}'|xargs`

if [ ! -z $oapPID ]; then
  kill -9 $oapPID
  echo 'SkyWalking OAP stoped successfully!'
else
  echo 'SkyWalking OAP not exist!'
fi

if [ ! -z $webPID ]; then
  kill -9 $webPID
  echo 'SkyWalking UI stoped successfully!'
else
  echo 'SkyWalking UI not exist!'
fi