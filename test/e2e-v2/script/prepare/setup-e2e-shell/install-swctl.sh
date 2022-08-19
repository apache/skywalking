#!/usr/bin/env bash

# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

BASE_DIR=$1
BIN_DIR=$2

install_swctl() {
  mkdir -p $BASE_DIR/swctl && cd $BASE_DIR/swctl
  curl -kLo skywalking-cli.tar.gz https://github.com/apache/skywalking-cli/archive/${SW_CTL_COMMIT}.tar.gz
  tar -zxf skywalking-cli.tar.gz --strip=1
  VERSION=${SW_CTL_COMMIT} make install DESTDIR=$BIN_DIR
}

if ! command -v swctl &> /dev/null; then
  echo "swctl is not installed"
  install_swctl
elif ! swctl --version | grep -q "${SW_CTL_COMMIT}"; then
  # Check if the installed version is correct
  echo "swctl is already installed, but version is not ${SW_CTL_COMMIT}, will re-install it"
  install_swctl
fi
