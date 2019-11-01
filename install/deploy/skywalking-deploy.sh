#!/bin/bash
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

set -e

CHART_PATH="../kubernetes/helm"
DPELOY_NAMESPACE="istio-system"
NEED_CHECK_PREFIX="deployment/skywalking-skywalking-"
ALS_ENABLED=true
MIXER_ENABLED=true

cd ${CHART_PATH}

and_stable_repo(){
  STABLE_REPO="https://kubernetes-charts.storage.googleapis.com/"
  helm repo add stable $STABLE_REPO

  STATUS_CMD=`echo $?`
  CHECK_REPO_CMD=`helm repo list | grep $STABLE_REPO | wc -l`
  echo "$STATUS_CMD"
  echo "$CHECK_REPO_CMD"
  while [[ $STATUS_CMD != 0 && $CHECK_REPO_CMD -ge 1 ]]
  do
    sleep 5
    helm repo add stable $STABLE_REPO

    STATUS_CMD=`echo $?`
    CHECK_REPO_CMD=`helm repo list | grep $STABLE_REPO | wc -l`
  done
}

and_stable_repo

helm dep up skywalking

helm -n $DPELOY_NAMESPACE install skywalking skywalking --set oap.istio.adapter.enabled=$MIXER_ENABLED --set oap.envoy.als.enabled=$ALS_ENABLED

for component in $NEED_CHECK_PREFIX"oap" $NEED_CHECK_PREFIX"ui" ; do
  kubectl -n istio-system rollout status $component --timeout 3m
done

echo "SkyWalking deployed successfully"
