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

set -x

K8S_VER=${K8S_VER:-'k8s-v1.18.0'}

function waitMinikube() {
  set +e
  kubectl cluster-info
  # This for loop waits until kubectl can access the api server that Minikube has created.
  for _ in {1..24}; do # Timeout for 240 seconds.
    kubectl get po --all-namespaces
    if [ $? -ne 1 ]; then
      break
    fi
    sleep 10
  done
  if ! kubectl get all --all-namespaces; then
    echo "Kubernetes failed to start"
    ps ax
    netstat -an
    docker images
    cat /var/lib/localkube/localkube.err
    printf '\n\n\n'
    kubectl cluster-info dump
    exit 1
  fi

  echo "Minikube is running"

  for _ in {1..6}; do # Timeout for 60 seconds.
    echo "$(sudo -E minikube ip) minikube.local" | sudo tee -a /etc/hosts
    ip=$(cat /etc/hosts | grep minikube.local | cut -d' ' -f1 | xargs)
    if [ -n "$ip" ]; then
      break
    fi
    sleep 10
  done

  ip=$(cat /etc/hosts | grep minikube.local | cut -d' ' -f1 | xargs)
  if [ -n "$ip" ]; then
    echo "minikube.local is mapped to $ip"
  else
    exit 1
  fi
}

# startMinikubeNone starts real kubernetes minikube with none driver. This requires `sudo`.
function startMinikubeNone() {
  export MINIKUBE_WANTUPDATENOTIFICATION=false
  export MINIKUBE_WANTREPORTERRORPROMPT=false
  export MINIKUBE_HOME=$HOME
  export CHANGE_MINIKUBE_NONE_USER=true

  sudo -E minikube config set WantUpdateNotification false
  sudo -E minikube config set WantReportErrorPrompt false
  sudo -E minikube start --kubernetes-version=${K8S_VER#k8s-} --driver=none
}

function stopMinikube() {
  sudo minikube stop
}

case "$1" in
  start) startMinikubeNone ;;
  stop) stopMinikube ;;
  wait) waitMinikube ;;
esac
