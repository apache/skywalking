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

# Add istio official repo
add_repo(){
  VERSION=$1
  REPO="https://storage.googleapis.com/istio-release/releases/${VERSION}/charts/"
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
  NAMESPACE=$1
  kubectl create ns ${NAMESPACE}

  STATUS_CMD=`echo $?`
  while [[ $STATUS_CMD != 0 ]]
  do
    sleep 5
    kubectl create ns ${NAMESPACE}
    STATUS_CMD=`echo $?`
  done
}

# Create CRD need for istio
create_crd() {
  NAMESPACE=$1
  helm install istio-init istio/istio-init -n ${NAMESPACE}
  CRD_COUNT=`kubectl get crds | grep 'istio.i' | wc -l`

  while [[ ${CRD_COUNT} != 23 ]]
  do
    sleep 5
    CRD_COUNT=`kubectl get crds | grep 'istio.io' | wc -l`
  done

  echo 'Istio crd create successful'
}

# Deploy istio related components
deploy_istio() {
  NAMESPACE=$1
  VERSION=$2
  helm pull istio/istio && tar zxvf istio-${VERSION}.tgz && rm istio-${VERSION}.tgz
  helm install istio istio -n ${NAMESPACE}

  check() {
     kubectl -n ${NAMESPACE}  get deploy | grep istio | awk '{print "deployment/"$1}' | while read line ;
     do
       kubectl rollout status $line -n ${NAMESPACE} --timeout 3m
     done
  }
  check

  echo "Istio is deployed successful"
}

add_mixer_template() {
  VERSION=$1
  kubectl apply -f https://raw.githubusercontent.com/istio/istio/$VERSION/mixer/template/metric/template.yaml
}

main(){
  ISTIO_VERSION="1.3.3"
  ISTIO_NAMESPACE="istio-system"
  add_repo $ISTIO_VERSION
  if [[ `kubectl get ns | grep $ISTIO_NAMESPACE | wc -l ` == 0 && `kubectl get ns $ISTIO_NAMESPACE | grep -v NAME | wc -l` == 0 ]] ;then
    create_namespace $ISTIO_NAMESPACE
  fi
  create_crd $ISTIO_NAMESPACE
  deploy_istio $ISTIO_NAMESPACE $ISTIO_VERSION
  add_mixer_template $ISTIO_VERSION
}

main
