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

# Detect files' changed set
# --includes: includes these files when detecting changed file sets, defaults to ^.*$, meaning all files will be checked
# --excludes: excludes these files when detecting changed file sets
# exit with status code 0 if no changed file matches the patterns, otherwise exit with status code non 0

includes=('^.*$')
excludes=()

including=0
excluding=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --includes)
      including=1
      excluding=0
      includes=()
      ;;
    --excludes)
      including=0
      excluding=1
      ;;
    *)
      if [[ ${including} -eq 1 ]]; then
        includes+=($1)
      elif [[ ${excluding} -eq 1 ]]; then
        excludes+=($1)
      fi
      ;;
  esac
  shift
done

changed_files=$(git diff --name-only origin/${ghprbTargetBranch:-master}..${ghprbActualCommit:-HEAD})

for file in ${changed_files}; do
  excluded=0
  for exclude in ${excludes[@]}; do
    if [[ ${file} =~ ${exclude} ]]; then
      excluded=1
      break
    fi
  done
  if [[ ${excluded} -eq 1 ]]; then
    echo "${file} is excluded"
    continue
  fi
  for include in ${includes[@]}; do
    if [[ ${file} =~ ${include} ]]; then
      echo "${file} is changed"
      exit 1
    fi
  done
done

exit 0
