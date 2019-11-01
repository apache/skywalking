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
# cleanup
BOOKINFO_VERSION="1.3"
MIXER_VERSION="1.3.3"
NAMESPACE="istio-system"

for release in istio istio-init skywalking ; do
  helm -n $NAMESPACE uninstall ${release}
done
kubectl delete crd `kubectl get crd | grep istio | awk '{print $1}'`
kubectl delete ns istio-system
kubectl delete -f https://raw.githubusercontent.com/istio/istio/release-${BOOKINFO_VERSION}/samples/bookinfo/platform/kube/bookinfo.yaml
kubectl delete -f https://raw.githubusercontent.com/istio/istio/${MIXER_VERSION}/mixer/template/metric/template.yaml