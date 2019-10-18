#!/usr/bin/env bash
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

export MAVEN_OPTS='-Dmaven.repo.local=.m2/repository -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC -XX:-UseGCOverheadLimit -Xmx3g'

MYSQL_URL="http://central.maven.org/maven2/mysql/mysql-connector-java/8.0.13/mysql-connector-java-8.0.13.jar"
MYSQL_DRIVER="mysql-connector-java-8.0.13.jar"
TMP_APP_YML="tmp_app.yml"

base_dir=$(pwd)
build=0
fast_fail=0
use_mysql=0
cases=()

# Parse the arguments
# --build-dist: build the distribution package ignoring the existance of `dist` folder, useful when running e2e locally
# --fast-fail: when testing multiple cases, skip following cases when a previous one failed
# --use-mysql: use MySQL as storage provider or not

while [ $# -gt 0 ]; do
  case "$1" in
    --build)
      build=1
      ;;
    --fast-fail)
      fast_fail=1
      ;;
    --use-mysql)
      use_mysql=1
      ;;
    *)
      cases+=($1)
  esac
  shift
done

[ ! -f "$base_dir/mvnw" ] \
  && echo 'Please run run.sh in the root directory of SkyWalking' \
  && exit 1

[ ${#cases[@]} -le 0 ] \
  && echo 'Usage: sh test/e2e/run.sh [--build-dist] [--fast-fail] <case1 maven module>[<case2 maven module>...<caseN maven module>]' \
  && exit 1

[ $build -eq 1 ] \
  && echo 'Building distribution package...' \
  && ./mvnw -q -Dcheckstyle.skip -Drat.skip -T2 -Dmaven.compile.fork -DskipTests clean install

echo "Running cases: $(IFS=$' '; echo "${cases[*]}")"

for test_case in "${cases[@]}"
do
  echo "Running case: $test_case"

  [ -d "$base_dir/$test_case" ] && rm -rf "$base_dir/$test_case"

  # Some of the tests will modify files in the distribution folder, e.g. cluster test will modify the application.yml
  # so we give each test a separate distribution folder here
  mkdir -p "$test_case" && tar -zxf dist/apache-skywalking-apm-bin.tar.gz -C "$test_case"
  
  sky_home_dir="${base_dir}/$test_case/apache-skywalking-apm-bin"
  
  if ${use_mysql} -eq 1; then
      echo "MySQL database is storage provider..."
      
      # Download MySQL connector.
      curl ${MYSQL_URL} > "${sky_home_dir}/oap-libs/${MYSQL_DRIVER}"
      [[ $? -ne 0 ]] && echo "Fail to download ${MYSQL_DRIVER}." && exit 1
      
      # Modify application.yml to set MySQL as storage provider.
      cat "${sky_home_dir}/conf/application.yml" | sed '/elasticsearch/,/mysql/d' | sed "/storage:/a \  mysql:" | sed "/storage:/,/receiver-sharing-server:/s/#//" > ${TMP_APP_YML}
      cat ${TMP_APP_YML} > "${sky_home_dir}/conf/application.yml"
      rm -f ${TMP_APP_YML}
  fi
  
  ./mvnw -Dbuild.id="${BUILD_ID:-local}" -Dsw.home="${sky_home_dir}" -f test/e2e/pom.xml -pl "$test_case" -am verify

  status_code=$?

  [ $status_code -ne 0 ] \
    && [ $fast_fail -eq 1 ] \
    && echo "Fast failing due to previous failure: $test_case, exit status code: $status_code" \
    && exit $status_code
done

exit 0
