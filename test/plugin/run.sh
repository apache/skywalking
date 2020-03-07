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

home="$(cd "$(dirname $0)"; pwd)"
scenario_name=""
parallel_run_size=1
force_build="off"
cleanup="off"

mvnw=${home}/../../mvnw
agent_home=${home}"/../../skywalking-agent"
scenarios_home="${home}/scenarios"

print_help() {
    echo  "Usage: run.sh [OPTION] SCENARIO_NAME"
    echo -e "\t-f, --force_build \t\t do force to build Plugin-Test tools and images"
    echo -e "\t--parallel_run_size, \t\t parallel size of test cases. Default: 1"
    echo -e "\t--cleanup, \t\t\t remove the related images and directories"
}

parse_commandline() {
    _positionals_count=0
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
            --parallel_run_size)
                test $# -lt 2 && exitWithMessage "Missing value for the optional argument '$_key'."
                parallel_run_size="$2"
                shift
                ;;
            --parallel_run_size=*)
                parallel_run_size="${_key##--parallel_run_size=}"
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
            -h*)
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
    num_of_testcases="`ls -l ${task_state_house} |grep -c FINISH`"
    [[ $1 -eq 1 ]] && printSystemInfo
    printf "Scenarios: %s, Testcases: %d, parallel_run_size: %d, Elapsed: %02d:%02d:%02d \n" \
        ${scenario_name} "${num_of_testcases}" "${parallel_run_size}" \
        $(( ${elapsed}/3600 )) $(( ${elapsed}%3600/60 )) $(( ${elapsed}%60 ))
    exit $1
}

printSystemInfo(){
  bash ${home}/script/systeminfo.sh
}

waitForAvailable() {
    while [[ `ls -l ${task_state_house} |grep -c RUNNING` -ge ${parallel_run_size} ]]
    do
        sleep 2
    done

    if [[ `ls -l ${task_state_house} |grep -c FAILURE` -gt 0 ]]; then
        exitAndClean 1
    fi
}

do_cleanup() {
    images=$(docker images -q "skywalking/agent-test-*:${BUILD_NO:=local}")
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
[[ "$force_build" == "on" ]] && ${mvnw} --batch-mode -f ${home}/pom.xml clean package -DskipTests -DBUILD_NO=${BUILD_NO:=local} docker:build

workspace="${home}/workspace/${scenario_name}"
task_state_house="${workspace}/.states"
[[ -d ${workspace} ]] && rm -rf $workspace
mkdir -p ${task_state_house}

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

supported_versions=`grep -v -E "^$|^#" ${supported_version_file}`
for version in ${supported_versions}
do
    waitForAvailable
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
        -Ddocker.image.version=${BUILD_NO:=local} \
        ${plugin_runner_helper} 1>${case_work_logs_dir}/helper.log

    [[ $? -ne 0 ]] && exitWithMessage "${testcase_name}, generate script failure!"

    echo "start container of testcase.name=${testcase_name}"
    bash ${case_work_base}/scenario.sh ${task_state_house} 1>${case_work_logs_dir}/${testcase_name}.log &
    sleep 3
done

echo -e "\033[33m${scenario_name} has already sumbitted\033[0m"

# wait to finish
while [[ `ls -l ${task_state_house} |grep -c RUNNING` -gt 0 ]]; do
    sleep 1
done

if [[ `ls -l ${task_state_house} |grep -c FAILURE` -gt 0 ]]; then
    exitAndClean 1
fi

exitAndClean 0
