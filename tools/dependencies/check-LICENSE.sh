#!/usr/bin/env bash

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

tar -zxf dist/apache-skywalking-apm-bin.tar.gz -C dist

# List all modules(jars) that belong to the SkyWalking itself, these will be ignored
# when checking the dependency licenses
./mvnw --batch-mode -Pbackend -Dexec.executable='echo' -Dexec.args='${project.artifactId}-${project.version}.jar' exec:exec -q > self-modules.txt

ls dist/apache-skywalking-apm-bin/oap-libs > all-dependencies.txt

# Exclude all self modules(jars) to generate all third-party dependencies
grep -vf self-modules.txt all-dependencies.txt > third-party-dependencies.txt

# Compare the third-party dependencies with known dependencies, expect that
# all third-party dependencies are KNOWN and the exit code of the command is 0,
# otherwise we should add its license to LICENSE file and add the dependency to known-oap-backend-dependencies.txt.
# Unify the `sort` behaviour: here we'll sort them again in case that the behaviour of `sort` command in target OS is different from what we
# used to sort the file `known-oap-backend-dependencies.txt`,
# i.e. "sort the two file using the same command (and default arguments)"
diff -w -B -U0 <(cat tools/dependencies/known-oap-backend-dependencies.txt | sort) <(cat third-party-dependencies.txt | sort)

status=$?

[[ ${status} -ne 0 ]] && exit ${status}

# Check ES7 distribution package

tar -zxf dist/apache-skywalking-apm-bin-es7.tar.gz -C dist

ls dist/apache-skywalking-apm-bin-es7/oap-libs > all-dependencies-es7.txt

grep -vf self-modules.txt all-dependencies-es7.txt > third-party-dependencies-es7.txt

diff -w -B -U0 <(cat tools/dependencies/known-oap-backend-dependencies-es7.txt | sort) <(cat third-party-dependencies-es7.txt | sort)
