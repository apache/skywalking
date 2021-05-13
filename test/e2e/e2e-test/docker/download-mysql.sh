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

set -ex

apk add curl

SW_HOME=/skywalking
MYSQL_URL="https://repo.maven.apache.org/maven2/mysql/mysql-connector-java/8.0.13/mysql-connector-java-8.0.13.jar"
MYSQL_DRIVER="mysql-connector-java-8.0.13.jar"

if ! curl -Lo "${SW_HOME}/oap-libs/${MYSQL_DRIVER}" ${MYSQL_URL}; then
    echo "Fail to download ${MYSQL_DRIVER}."
    exit 1
fi
