#!/bin/bash
#
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

wkdir="$(cd "$(dirname $0)"; pwd)"
MVNW=${wkdir}/../../mvnw

type=
artifactId=
scenario_home=
scenario_case=
scenarios_home=${wkdir}/scenarios
confirm=

while [[ -z ${scenario_name} ]]; do
  echo "Sets the scenario name"
  read -p ">: " scenario_name
done

while [[ ${type} != "jvm" && ${type} != "tomcat" ]]; do
  echo "Chooses a type of container, 'jvm' or 'tomcat', which is 'jvm-container' or 'tomcat-container'"
  read -p ">: " type
done

echo "Gives an artifactId for your project (default: ${scenario_name})"
read -p ">: " artifactId
[[ -z ${artifactId} ]] && artifactId=${scenario_name}


echo "Sets the entry name of scenario (default: ${scenario_name})"
read -p ">: " scenario_case
[[ -z ${scenario_case} ]] && scenario_case=${scenario_name}

echo ""
echo -e "scenario_home: ${scenario_name}"
echo -e "type: ${type}"
echo -e "artifactId: ${artifactId}"
echo -e "scenario_case: ${scenario_case}"
echo ""

while [[ ${confirm} != "Y" && ${confirm} != "N" && ${confirm} != "y" && ${confirm} != "n" ]]; do
  echo "Please confirm: [Y/N]"
  read -p ">: " confirm
done

if [[ ${confirm} == "N" || ${confirm} == "n" ]]; then
  exit 0
fi

${MVNW} -f ${wkdir}/archetypes/pom.xml clean install

package="org.apache.skywalking.apm.testcase.${artifactId%%-scenario}"
${MVNW} archetype:generate \
    -Dfilter=org.apache.skywalking.apm.testcase:${artifactId}:"1.0.0" \
    -DarchetypeGroupId=org.apache.skywalking.plugin \
    -DoutputDirectory=${scenarios_home} \
    -Dscenario_name=${scenario_name} \
    -Dscenario_case=${scenario_case} \
    -DarchetypeArtifactId=${type} \
    -DarchetypeCatalog=local \
    -DinteractiveMode=false \
    -DarchetypeVersion=1.0.0 \
    -Dpackage=${package}
