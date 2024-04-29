#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo "Waiting for startup.."
until mongosh --host mongodb-1:27017 --eval 'quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)' &>/dev/null; do
  printf '.'
  sleep 1
done
until mongosh --host mongodb-2:27017 --eval 'quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)' &>/dev/null; do
  printf '.'
  sleep 1
done

echo "Started.."

mongosh --host mongodb-1:27017 <<EOF
     var cfg = {
      "_id": "rs1",
      "protocolVersion": 1,
      "members": [
         {
           "_id": 0,
           "host": "mongodb-1:27017"
         },
         {
           "_id": 1,
           "host": "mongodb-2:27017"
         }
      ]
     };
     rs.initiate(cfg, { force: true });
     rs.reconfig(cfg, { force: true });
EOF