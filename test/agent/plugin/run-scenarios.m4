#!/usr/bin/env bash
#ARG_POSITIONAL_INF([scenario], [The scenario that you want to running])
#DEFINE_SCRIPT_DIR([AGENT_TEST_PLUGIN_HOME])
#ARG_HELP()
#ARGBASH_GO
# [
SCENARIO_PACKAGES_TARGET_DIRECTORY=${AGENT_TEST_PLUGIN_HOME}/dist
SCENARIO_CASES_HOME=${AGENT_TEST_PLUGIN_HOME}/testcase && mkdir -p ${SCENARIO_CASES_HOME}
MVN_EXEC=${AGENT_TEST_PLUGIN_HOME}/../../../mvnw

UNPACKED_VERSION=()
for SCENARIO in ${_arg_scenario[@]}
do
  # check if the scenario directory is existing
  SCENARIO_HOME=${AGENT_TEST_PLUGIN_HOME}/${SCENARIO}
  #
  SUPPORT_VERSION_FILE=${SCENARIO_HOME}/support-version.list
  SUPPORT_VERSIONS=($(cat $SUPPORT_VERSION_FILE))
  # echo "Support version: ${SUPPORT_VERSIONS[@]}"

  MVN_PROFILES=""
  for SCENARIO_VERSION in ${SUPPORT_VERSIONS[@]}
  do
    MVN_PROFILES="$MVN_PROFILES,${SCENARIO}-${SCENARIO_VERSION}"
  done
  cd ${SCENARIO_HOME} && ${MVN_EXEC} clean write-text-files:write-text-files package -P ${MVN_PROFILES}

  for SCENARIO_VERSION in ${SUPPORT_VERSIONS[@]}
  do
    # check if the scenario package is exist. if not. record it and then throw an exception

    tar -zxvf ${SCENARIO_PACKAGES_TARGET_DIRECTORY}/${SCENARIO}-${SCENARIO_VERSION}.tar.gz -C ${SCENARIO_CASES_HOME} > /dev/null 2>&1
    bash ${SCENARIO_CASES_HOME}/${SCENARIO}-${SCENARIO_VERSION}/scenario.sh
  done
done


# ]
