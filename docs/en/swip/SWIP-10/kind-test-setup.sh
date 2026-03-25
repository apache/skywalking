#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# SWIP-10 Local Verification: Envoy AI Gateway + SkyWalking OTLP on Kind
#
# Prerequisites:
#   - kind, kubectl, helm, docker installed
#   - Docker images pulled (or internet access for Kind to pull)
#
# This script sets up a Kind cluster with:
#   - Envoy Gateway (v1.3.3) + AI Gateway controller (v0.5.0)
#   - Ollama (in-cluster) with a small model
#   - OTel Collector (debug exporter) to capture OTLP metrics and logs
#   - AI Gateway configured with SkyWalking-compatible OTLP resource attributes
#
# Usage:
#   ./kind-test-setup.sh          # Full setup
#   ./kind-test-setup.sh cleanup  # Delete the cluster

set -e

CLUSTER_NAME="aigw-swip10-test"

if [ "$1" = "cleanup" ]; then
  echo "Cleaning up..."
  kind delete cluster --name $CLUSTER_NAME
  exit 0
fi

echo "=== Step 1: Create Kind cluster ==="
kind create cluster --name $CLUSTER_NAME

echo "=== Step 2: Pre-load Docker images ==="
IMAGES=(
  "envoyproxy/ai-gateway-controller:v0.5.0"
  "envoyproxy/ai-gateway-extproc:v0.5.0"
  "envoyproxy/gateway:v1.3.3"
  "envoyproxy/envoy:distroless-v1.33.3"
  "otel/opentelemetry-collector:latest"
  "ollama/ollama:latest"
)
for img in "${IMAGES[@]}"; do
  echo "Pulling $img..."
  docker pull "$img"
  echo "Loading $img into Kind..."
  kind load docker-image "$img" --name $CLUSTER_NAME
done

echo "=== Step 3: Install Envoy Gateway ==="
# enableBackend is required for Backend resources used by AIServiceBackend
helm install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.3.3 -n envoy-gateway-system --create-namespace \
  --set config.envoyGateway.extensionApis.enableBackend=true
kubectl wait --for=condition=available deployment/envoy-gateway \
  -n envoy-gateway-system --timeout=120s

echo "=== Step 4: Install AI Gateway ==="
helm upgrade -i aieg-crd oci://docker.io/envoyproxy/ai-gateway-crds-helm \
  --namespace envoy-ai-gateway-system --create-namespace
helm upgrade -i aieg oci://docker.io/envoyproxy/ai-gateway-helm \
  --namespace envoy-ai-gateway-system --create-namespace
kubectl wait --for=condition=available deployment/ai-gateway-controller \
  -n envoy-ai-gateway-system --timeout=120s

echo "=== Step 5: Deploy test resources ==="
kubectl apply -f kind-test-resources.yaml

echo "=== Step 6: Wait for pods ==="
sleep 10
kubectl wait --for=condition=available deployment/ollama -n default --timeout=120s
kubectl wait --for=condition=available deployment/otel-collector -n default --timeout=60s

echo "=== Step 7: Pull Ollama model ==="
OLLAMA_POD=$(kubectl get pod -l app=ollama -o jsonpath='{.items[0].metadata.name}')
kubectl exec "$OLLAMA_POD" -- ollama pull qwen2.5:0.5b

echo "=== Step 8: Wait for Envoy pod ==="
sleep 30
kubectl get pods -A

echo ""
echo "=== Setup complete ==="
echo "To test:"
echo "  kubectl port-forward -n envoy-gateway-system svc/envoy-default-my-ai-gateway-76d02f2b 8080:80 &"
echo "  curl -s --noproxy '*' http://localhost:8080/v1/chat/completions \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"model\":\"qwen2.5:0.5b\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hi\"}]}'"
echo ""
echo "To check OTLP output:"
echo "  kubectl logs -l app=otel-collector | grep -A 20 'ResourceMetrics\\|ResourceLog'"
echo ""
echo "To cleanup:"
echo "  ./kind-test-setup.sh cleanup"
