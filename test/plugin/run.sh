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
build_id="latest"
cleanup="off"

mvnw=${home}/../../mvnw
agent_home=${home}"/../../skywalking-agent"
scenarios_home="${home}/scenarios"


print_help() {
    echo  "Usage: run.sh [OPTION] SCENARIO_NAME"
    echo -e "\t-f, --force_build \t\t do force to build Plugin-Test tools and images"
    echo -e "\t--build_id, \t\t\t specify Plugin_Test's image tag. Defalt: latest"
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
            --build_id)
                test $# -lt 2 && exitWithMessage "Missing value for the optional argument '$_key'."
                build_id="$2"
                shift
                ;;
            --build_id=*)
                build_id="${_key##--build_id=}"
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
    printf "Scenarios: %s, Testcases: %d, parallel_run_size: %d, Elapsed: %02d:%02d:%02d \n" \
        ${scenario_name} "${num_of_testcases}" "${parallel_run_size}" \
        $(( ${elapsed}/3600 )) $(( ${elapsed}%3600/60 )) $(( ${elapsed}%60 ))
    exit $1
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
    docker images -q "skywalking/agent-test-*:${build_id}" | xargs -r docker rmi -f
    [[ -d ${home}/dist ]] && rm -rf ${home}/dist
    [[ -d ${home}/workspace ]] && rm -rf ${home}/workspace
}

check_scenario_name_param() {
    if test -z "$scenario_name"; then
        echo "Missing value for the scenario argument"
        exit 0
    fi
}

start_stamp=`date +%s`
parse_commandline "$@"
check_scenario_name_param()


if [[ "$cleanup" == "on" ]]; then
    do_cleanup
    exit 0
fi

if [[ ! -d ${agent_home} ]]; then
    echo "[WARN] SkyWalking Agent not exists"
    ${mvnw} -f ${home}/../../pom.xml -Pagent -DskipTests clean package 
fi
[[ "$force_build" == "on" ]] && ${mvnw} -f ${home}/pom.xml clean package -DskipTests -Dbuild_id=${build_id} docker:build

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
mode=`grep "runningMode" ${scenario_home}/configuration.yml |sed -e "s/\s//g" |awk -F: '{print $2}'`
if [[ "$mode" == "with_optional" ]]; then
    agent_with_optional_home=${home}/workspace/agent_with_optional
    if [[ ! -d ${agent_with_optional_home} ]]; then
        mkdir -p ${agent_with_optional_home}
        cp -r ${agent_home}/* ${agent_with_optional_home}
        mv ${agent_with_optional_home}/optional-plugins/* ${agent_with_optional_home}/plugins/
    fi
    _agent_home=${agent_with_optional_home}
elif [[ "$mode" == "with_bootstrap" ]]; then
    agent_with_bootstrap_home=${home}/workspace/agent_with_bootstrap
    if [[ ! -d ${agent_with_bootstrap_home} ]]; then
        mkdir -p ${agent_with_bootstrap_home}
        cp -r ${agent_home}/* ${agent_with_bootstrap_home}
        mv ${agent_with_bootstrap_home}/bootstrap-plugins/* ${agent_with_bootstrap_home}/plugins/
    fi
    _agent_home=${agent_with_bootstrap_home}
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
    ${mvnw} clean package -Dtest.framework.version=${version} && \
        mv ./target/${scenario_name}.* ${case_work_base}

    java -jar \
        -Xmx256m -Xms256m \
        -Dconfigure.file=${scenario_home}/configuration.yml \
        -Dscenario.home=${case_work_base} \
        -Dscenario.name=${scenario_name} \
        -Dscenario.version=${version} \
        -Doutput.dir=${case_work_base} \
        -Dagent.dir=${_agent_home} \
        -Ddocker.image.version=${build_id} \
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
