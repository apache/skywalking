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

# Add istio official repo
add_repo(){
  REPO="https://storage.googleapis.com/istio-release/releases/1.3.3/charts/"
  helm repo add istio $REPO

  STATUS_CMD=`echo $?`
  CHECK_REPO_CMD=`helm repo list | grep $REPO | wc -l`
  echo "$STATUS_CMD"
  echo "$CHECK_REPO_CMD"
  while [[ $STATUS_CMD != 0 && $CHECK_REPO_CMD -ge 1 ]]
  do
    sleep 5
    helm repo add istio $REPO

    STATUS_CMD=`echo $?`
    CHECK_REPO_CMD=`helm repo list | grep $REPO | wc -l`
  done
}

# Create istio-system namespace
create_namespace() {
  kubectl create ns istio-system

  STATUS_CMD=`echo $?`
  while [[ $STATUS_CMD != 0 ]]
  do
    sleep 5
    kubectl create ns istio-system
    STATUS_CMD=`echo $?`
  done
}

# Create CRD need for istio
create_crd() {
  helm install istio-init istio/istio-init -n istio-sytem
  CRD_COUNT=`kubectl get crds | grep 'istio.io' | wc -l`

  while [[ ${CRD_COUNT} != 23 ]]
  do
    sleep 5
    CRD_COUNT=`kubectl get crds | grep 'istio.io' | wc -l`
  done

  echo 'Istio crd create successful'
}

# Deploy istio related components
deploy_istio() {
  helm install istio istio/istio -n istio-system

  check() {
     kubectl -n istio-system  get deploy | grep istio | awk '{print "deployment/"$1}' | while read line ;
     do
       kubectl rollout status $line -n istio-system;
     done
  }
  check

  echo "Istio is deployed successful"
}

main(){
  add_repo
  create_namespace
  create_crd
  deploy_istio
}

main