#!/usr/bin/env sh

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

bin_path=$0
exporter_dir=$(cd $(dirname $0); pwd)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --taskid=*)
      task_id=${1#*=}
      ;;
    --traceid=*)
      trace_id=${1#*=}
      ;;
    *)
      export_path=$1
  esac
  shift
done

[[ ! ${task_id} || ! ${trace_id} || ! ${export_path} ]] \
  && echo 'Usage: sh tools/profile-exporter/profile_exporter.sh [--taskid] [--traceid] export_path' \
  && exit 1

[[ ! -d ${export_path} ]] \
  && echo "Cannot find export export_path path: ${export_path}" \
  && exit 1

# prepare paths
oap_libs_dir="${exporter_dir}/../../oap-libs"
exporter_log_file="${exporter_dir}/profile_exporter_log4j2.xml"
tool_application_config="${exporter_dir}/application.yml"
[[ ! -f ${tool_application_config} ]] \
  && echo "Cannot find oap application.yml" \
  && exit 1
[[ ! -d ${oap_libs_dir} ]] \
  && echo "Cannot find oap libs path" \
  && exit 1

# create current trace temporary path
work_dir="${export_path}/${trace_id}"
mkdir -p ${work_dir}

# prepare exporter files
mkdir -p "${work_dir}/config"
mkdir -p "${work_dir}/work"
cp ${exporter_log_file} ${work_dir}/config/log4j2.xml
# only persist core and storage module in application.yml config
cp ${tool_application_config} ${work_dir}/config/application.yml

# start export
echo "Exporting task: ${task_id}, trace: ${trace_id}, export_path: ${work_dir}"
JAVA_OPTS=" -Xms256M -Xmx512M"
_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=java

CLASSPATH="${work_dir}/config:$CLASSPATH"
for i in "${oap_libs_dir}"/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

exec $_RUNJAVA ${JAVA_OPTS} -classpath $CLASSPATH org.apache.skywalking.oap.server.tool.profile.exporter.ProfileSnapshotExporter \
  ${task_id} ${trace_id} ${work_dir}/work &
wait

if [ `ls -l ${work_dir}/work | wc -l` -lt 2 ]; then
	echo "Export failure!"
	exit 1
fi

# compress files(only compress work data, no config)
echo "Compressing exported directory: ${work_dir}"
CURRENT_DIR="$(cd "$(dirname $0)"; pwd)"
cd ${work_dir}
tar zcvf "${trace_id}.tar.gz" "./work"
mv "${trace_id}.tar.gz" "../"
cd $CURRENT_DIR

# clear work files
rm -rf "${work_dir}"

echo "Profile export finished: ${work_dir}.tar.gz"
