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
# limitation under the License.

wkdir="$(cd "$(dirname $0)"; pwd)"
MVNW=${wkdir}/../../../mvnw

type="jvm"
artifactId=
scenario_home=
scenario_case=
scenarios_home=${wkdir}/../scenarios

exitWithMessage() {
    echo $1>&2
    exit 1
}

print_help() {
    echo -e "Usage: bash generator.sh -n my-scenario -c mycase -type jvm -a my"
    echo -e "\t--type:\t\t\tchooses a type of container, 'jvm' or 'tomcat', which is 'jvm-container' or 'tomcat-container'.(required)"
    echo -e "\t-a| --artifactId:\tgives an artifactId for your project."
    echo -e "\t-n| --scenario_name:\tsets the scenario name.(required)"
    echo -e "\t-c| --scenario_case:\tsets the entry name of scenario."
    exit 0
}

parse_commandline() {
    _positionals_count=0
    while test $# -gt 0
    do
        _key="$1"
        case "$_key" in
            --type=*)
                type="${_key##--type=}"
                ;;
            --type)
                test $# -lt 2 && exitWithMessage "Missing value for the optional argument '$_key'."
                type="$2"
                shift
                ;;
            --artifactId=*)
                artifactId="${_key##--artifactId=}"
                ;;
            -a| --artifactId)
                test $# -lt 2 && exitWithMessage "Missing value for the optional argument '$_key'."
                artifactId="$2"
                shift
                ;;
            --scenario_name=*)
                scenario_name="${_key##--scenario_name=}"
                ;;
            -n| --scenario_name)
                test $# -lt 2 && exitWithMessage "Missing value for the optional argument '$_key'."
                scenario_name="$2"
		echo "$2"
                shift
                ;;
            -c| --scenario_case)
                test $# -lt 2 && exitWithMessage "Missing value for the optional argument '$_key'."
                scenario_case="$2"
                shift
                ;;
            --scenario_case=*)
                scenario_name="${_key##--scenario_case=}"
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
        esac
        shift
    done
}

parse_commandline "$@"

echo "${type} ${scenario_name}"
[[ -z ${type} ]] && print_help
[[ -z ${scenario_name} ]] && print_help
[[ -z ${artifactId} ]] && artifactId=${scenario_name}
[[ -z ${scenario_case} ]] && scenario_case=${scenario_name}

${MVNW} -f ./pom.xml install

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
