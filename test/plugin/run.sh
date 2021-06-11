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

set -ex

home="$(cd "$(dirname $0)"; pwd)"
scenario_name=""
force_build="off"
cleanup="off"
debug_mode=

mvnw=${home}/../../mvnw
agent_home="${home}"/../../skywalking-agent
jacoco_home="${home}"/../jacoco
scenarios_home="${home}/scenarios"
num_of_testcases=

image_version="jdk8-1.0.0"
jacoco_version="${JACOCO_VERSION:-0.8.6}"

os="$(uname)"

print_help() {
    echo  "Usage: run.sh [OPTION] SCENARIO_NAME"
    echo -e "\t-f, --force_build \t\t do force to build Plugin-Test tools and images"
    echo -e "\t--cleanup, \t\t\t remove the related images and directories"
    echo -e "\t--debug, \t\t\t to save the log files and actualData.yaml"
}

parse_commandline() {
    while test $# -gt 0
    do
        _key="$1"
        case "$_key" in
            -f|--force_build)
                force_build="on"
                ;;
            --cleanup)
                cleanup="on"
                ;;
            --debug)
                debug_mode="on";
                ;;
            --image_version)
                image_version="$2"
                shift
                ;;
            --image_version=*)
                image_version="${_key##--image_version=}"
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
            *)
                scenario_name=$1
                ;;
        esac
        shift
    done
}

exitWithMessage() {
    echo -e "\033[31m[ERROR] $1\033[0m">&2
    exitAndClean 1
}

exitAndClean() {
    elapsed=$(( `date +%s` - $start_stamp ))
    [[ $1 -eq 1 ]] && printSystemInfo
    printf "Scenarios: ${scenario_name}, Testcases: ${num_of_testcases}, Elapsed: %02d:%02d:%02d \n" \
        $(( ${elapsed}/3600 )) $(( ${elapsed}%3600/60 )) $(( ${elapsed}%60 ))
    exit $1
}

printSystemInfo(){
  bash ${home}/script/systeminfo.sh
}

do_cleanup() {
    images=$(docker images -q "skywalking/agent-test-*")
    [[ -n "${images}" ]] && docker rmi -f ${images}
    images=$(docker images -qf "dangling=true")
    [[ -n "${images}" ]] && docker rmi -f ${images}

    docker network prune -f
    docker volume prune -f

    [[ -d ${home}/dist ]] && rm -rf ${home}/dist
    [[ -d ${home}/workspace ]] && rm -rf ${home}/workspace
}

agent_home_selector() {
    running_mode=$1
    with_plugins=$2

    plugin_dir="optional-plugins"
    target_agent_dir="agent_with_optional"
    if [[ "${running_mode}" != "with_optional" ]]; then
        plugin_dir="bootstrap-plugins"
        target_agent_dir="agent_with_bootstrap"
    fi

    target_agent_home=${workspace}/${target_agent_dir}
    mkdir -p ${target_agent_home}
    cp -fr ${agent_home}/* ${target_agent_home}

    with_plugins=`echo $with_plugins |sed -e "s/;/ /g"`
    for plugin in ${with_plugins};
    do
        mv ${target_agent_home}/${plugin_dir}/${plugin} ${target_agent_home}/plugins/
        [[ $? -ne 0 ]] && exitAndClean 1
    done
    _agent_home=${target_agent_home}
}

start_stamp=`date +%s`
parse_commandline "$@"

if [[ "$cleanup" == "on" ]]; then
    do_cleanup
    [[ -z "${scenario_name}" ]] && exit 0
fi

test -z "$scenario_name" && exitWithMessage "Missing value for the scenario argument"

if [[ ! -d ${agent_home} ]]; then
    echo "[WARN] SkyWalking Agent not exists"
    ${mvnw} --batch-mode -f ${home}/../../pom.xml -Pagent -DskipTests clean package
fi
# if it fails last time, relevant information will be deleted
if [ "$os" == 'Darwin' ]; then
    sed -i '' '/<sourceDirectory>scenarios\/'"$scenario_name"'<\/sourceDirectory>/d' ./pom.xml
else
    sed -i '/<sourceDirectory>scenarios\/'"$scenario_name"'<\/sourceDirectory>/d' ./pom.xml
fi
# add scenario_name into plugin/pom.xml
echo check code with the checkstyle-plugin
if [ "$os" == 'Darwin' ]; then
    sed -i '' '/<\/sourceDirectories>/i\'$'\n''<sourceDirectory>scenarios\/'"$scenario_name"'<\/sourceDirectory>'$'\n' ./pom.xml
else
    sed -i '/<\/sourceDirectories>/i <sourceDirectory>scenarios\/'"$scenario_name"'<\/sourceDirectory>' ./pom.xml
fi

if [[ "$force_build" == "on" ]]; then
    profile=
    [[ $image_version =~ "jdk14-" ]] && profile="-Pjdk14"
    ${mvnw} --batch-mode -f ${home}/pom.xml clean package -DskipTests ${profile}
fi
# remove scenario_name into plugin/pom.xml
if [ "$os" == 'Darwin' ]; then
    sed -i '' '/<sourceDirectory>scenarios\/'"$scenario_name"'<\/sourceDirectory>/d' ./pom.xml
else
    sed -i '/<sourceDirectory>scenarios\/'"$scenario_name"'<\/sourceDirectory>/d' ./pom.xml
fi

workspace="${home}/workspace/${scenario_name}"
[[ -d ${workspace} ]] && rm -rf $workspace

plugin_runner_helper="${home}/dist/plugin-runner-helper.jar"
if [[ ! -f ${plugin_runner_helper} ]]; then
    exitWithMessage "Plugin Runner tools not exists, Please re-try it with '-f'"
    print_helper
fi

echo "start submit job"
scenario_home=${scenarios_home}/${scenario_name} && cd ${scenario_home}

supported_version_file=${scenario_home}/support-version.list
if [[ ! -f $supported_version_file ]]; then
    exitWithMessage "cannot found 'support-version.list' in directory ${scenario_name}"
fi

_agent_home=${agent_home}
running_mode=$(grep "^runningMode" ${scenario_home}/configuration.yml |sed -e "s/ //g" |awk -F: '{print $2}')
with_plugins=$(grep "^withPlugins" ${scenario_home}/configuration.yml |sed -e "s/ //g" |awk -F: '{print $2}')

if [[ -n "${running_mode}" ]]; then
    [[ -z "${with_plugins}" ]] && exitWithMessage \
       "'withPlugins' is required configuration when 'runningMode' was set as 'optional_plugins' or 'bootstrap_plugins'"
    agent_home_selector ${running_mode} ${with_plugins}
fi

mkdir -p "${jacoco_home}"
ls "${jacoco_home}"/jacocoagent.jar || curl -Lso "${jacoco_home}"/jacocoagent.jar https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/${jacoco_version}/org.jacoco.agent-${jacoco_version}-runtime.jar
ls "${jacoco_home}"/jacococli.jar || curl -Lso "${jacoco_home}"/jacococli.jar https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/${jacoco_version}/org.jacoco.cli-${jacoco_version}-nodeps.jar

supported_versions=`grep -v -E "^$|^#" ${supported_version_file}`
for version in ${supported_versions}
do
    testcase_name="${scenario_name}-${version}"

    # testcase working directory, there are logs, data and packages.
    case_work_base=${workspace}/${version}
    mkdir -p ${case_work_base}/{data,logs}

    case_work_logs_dir=${case_work_base}/logs

    # copy expectedData.yml
    cp ./config/expectedData.yaml ${case_work_base}/data

    # echo "build ${testcase_name}"
    ${mvnw} --batch-mode clean package -Dtest.framework.version=${version} && \
        mv ./target/${scenario_name}.* ${case_work_base}

    java -jar \
        -Xmx256m -Xms256m \
        -Dconfigure.file=${scenario_home}/configuration.yml \
        -Dscenario.home=${case_work_base} \
        -Dscenario.name=${scenario_name} \
        -Dscenario.version=${version} \
        -Doutput.dir=${case_work_base} \
        -Dagent.dir=${_agent_home} \
        -Djacoco.home=${jacoco_home} \
        -Ddebug.mode=${debug_mode} \
        -Ddocker.image.version=${image_version} \
        ${plugin_runner_helper} 1>${case_work_logs_dir}/helper.log

    [[ $? -ne 0 ]] && exitWithMessage "${testcase_name}, generate script failure!"

    echo "start container of testcase.name=${testcase_name}"
    bash ${case_work_base}/scenario.sh $debug_mode 1>${case_work_logs_dir}/${testcase_name}.log
    status=$?
    if [[ $status == 0 ]]; then
        [[ -z $debug_mode ]] && rm -rf ${case_work_base}
    else
        exitWithMessage "Testcase ${testcase_name} failed!"
    fi
    num_of_testcases=$(($num_of_testcases+1))
done

echo -e "\033[33m${scenario_name} has already sumbitted\033[0m"

exitAndClean 0
