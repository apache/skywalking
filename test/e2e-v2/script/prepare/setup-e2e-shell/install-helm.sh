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
HELMVERSION=${HELMVERSION:-'helm-v3.0.0'}

if ! command -v helm &> /dev/null; then
  mkdir -p $BASE_DIR/helm && cd $BASE_DIR/helm
  curl -sSL https://get.helm.sh/${HELMVERSION}-linux-amd64.tar.gz
  tar xz -C $BIN_DIR --strip-components=1 linux-amd64/helm
fi