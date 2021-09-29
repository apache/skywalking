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

if ! command -v etcdctl &> /dev/null; then
  mkdir -p $BASE_DIR/etcdctl && cd $BASE_DIR/etcdctl
  utype=$(uname | awk '{print tolower($0)}')
  suffix=
  if [ $utype = "darwin" ]
  then
      suffix="zip"
  else
      suffix="tar.gz"
  fi
  curl -kLo etcdctl.$suffix https://github.com/coreos/etcd/releases/download/v3.5.0/etcd-v3.5.0-$utype-amd64.$suffix
  tar -zxf etcdctl.$suffix --strip=1
  cp etcdctl $BIN_DIR/
fi