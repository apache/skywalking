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

patterns=()
any_of=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --any-of)
      any_of=1
      ;;
    *)
      patterns+=($1)
      ;;
  esac
  shift
done

[[ ${#patterns[@]} -eq 0 ]] && echo 'No file pattern is specified, exiting' && exit 1

changed_files=$(git diff --name-only origin/${ghprbTargetBranch:-master}..${ghprbActualCommit:-HEAD})

test_results=()

for file in ${changed_files}; do
  for pattern in ${patterns[@]}; do
    if [[ ${file} =~ ${pattern} ]]; then
      test_results+=("Hit: ${file} matches pattern ${pattern}")
    else
      test_results+=("Miss: ${file} does not match pattern ${pattern}")
    fi
  done
done

IFS=$'\n' ; echo "${test_results[*]}"

if [[ ${any_of} -eq 1 ]]; then
  for test_result in ${test_results[@]}; do
    [[ ${test_result} =~ ^Hit:.+$ ]] && echo ${test_result} && exit 1
  done
fi

exit 0