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

CHART_PATH="./install/kubernetes/helm"
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

sudo sysctl -w vm.max_map_count=262144
sudo sysctl -w vm.drop_caches=1
sudo sysctl -w vm.drop_caches=3

helm -n $DPELOY_NAMESPACE install skywalking skywalking --set oap.istio.adapter.enabled=$MIXER_ENABLED \
        --set oap.envoy.als.enabled=$ALS_ENABLED --set oap.replicas=1

for component in $NEED_CHECK_PREFIX"oap" ; do
#  for i in {1..10} ;do
#    echo "*****************$i time*************"
#    free -lh
#    echo "*****************************************************"
#    kubectl -n ${DPELOY_NAMESPACE} get deploy -o wide
#    echo "*****************************************************"
#    if [[ `kubectl -n ${DPELOY_NAMESPACE} get deploy -o wide | grep skywalking-elasticsearch | awk '{print $2}'` == "1/1" ]] ;then
#      sleep 10
#      kubectl -n ${DPELOY_NAMESPACE} logs `kubectl -n ${DPELOY_NAMESPACE} get pod |grep skywalking-elasticsearch | awk '{print $1}'` --all-containers=true
#    fi
#    echo "*****************************************************"
#    kubectl -n ${DPELOY_NAMESPACE} get jobs -o wide
#    echo "*****************************************************"
#    kubectl -n ${DPELOY_NAMESPACE} get event  | grep -v "istio"
#    kubectl -n ${DPELOY_NAMESPACE} describe pod `kubectl -n ${DPELOY_NAMESPACE} get pod |grep elasticsearch | awk '{print $1}'`
#    echo "*****************************************************"
#    kubectl -n ${DPELOY_NAMESPACE} describe pod `kubectl -n ${DPELOY_NAMESPACE} get pod |grep skywalking-skywalking-oap | awk '{print $1}'`
#    sleep 10
#  done
#  echo "*****************************************************"
#  sleep 10

  #kubectl -n ${DPELOY_NAMESPACE} logs `kubectl -n ${DPELOY_NAMESPACE} get pod |grep skywalking-skywalking-oap | awk '{print $1}'` --all-containers=true
  #kubectl -n ${DPELOY_NAMESPACE} logs $component
  kubectl -n ${DPELOY_NAMESPACE} wait $component --for condition=available --timeout=600s
#  kubectl -n ${DPELOY_NAMESPACE} rollout status $component --timeout 10m
done

echo "SkyWalking deployed successfully"
