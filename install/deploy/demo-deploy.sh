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

# The script for bookinfo Application to deploy
BOOKINFO_VERSION="1.3"
kubectl label namespace default istio-injection=enabled
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-${BOOKINFO_VERSION}/samples/bookinfo/platform/kube/bookinfo.yaml

# check status
kubectl get deploy  | grep -E 'details|productpage|ratings|reviews' | awk '{print "deployment/"$1}' | while read deploy
do
  kubectl rollout status ${deploy}  --timeout 3m
done

# request
start=$(date "+%M")
while true
do
  REQUEST_STATUS=$(kubectl exec -it $(kubectl get pod -l app=ratings -o jsonpath='{.items[0].metadata.name}') -c ratings -- curl -m 10 productpage:9080/productpage | grep -o "<title>.*</title>" | wc -l)
  if [ $REQUEST_STATUS == 1 ]; then
      break
  fi
  end=$(date "+%M")
  time=$(($end - $start))
  if [ $time -ge 3 ]; then
      echo "Request timeout"
      break
  fi
done
