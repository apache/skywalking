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

{
  "identifier": {
    "node": {
      "id": "sidecar~172.18.0.8~productpage-v1-65576bb7bf-4mzsp.default~default.svc.cluster.local",
      "cluster": "productpage.default",
      "metadata": {
        "PROXY_CONFIG": {
          "concurrency": 2.0,
          "envoyAccessLogService": {
            "address": "0.tcp.ngrok.io:13760"
          },
          "statNameLength": 189.0,
          "configPath": "./etc/istio/proxy",
          "parentShutdownDuration": "60s",
          "proxyAdminPort": 15000.0,
          "controlPlaneAuthPolicy": "MUTUAL_TLS",
          "drainDuration": "45s",
          "proxyMetadata": {
            "DNS_AGENT": ""
          },
          "terminationDrainDuration": "5s",
          "tracing": {
            "zipkin": {
              "address": "zipkin.istio-system:9411"
            }
          },
          "statusPort": 15020.0,
          "serviceCluster": "productpage.default",
          "envoyMetricsService": {
          },
          "discoveryAddress": "istiod.istio-system.svc:15012",
          "binaryPath": "/usr/local/bin/envoy"
        },
        "PLATFORM_METADATA": {
          "gcp_location": "us-central1-a",
          "gcp_gce_instance_id": "2148869885222929334",
          "gcp_gce_instance": "zhenxu-test",
          "gcp_project_number": "191872121544",
          "gcp_project": "skywalking-live-demo"
        },
        "CLUSTER_ID": "Kubernetes",
        "APP_CONTAINERS": "productpage",
        "LABELS": {
          "service.istio.io/canonical-name": "productpage",
          "version": "v1",
          "security.istio.io/tlsMode": "istio",
          "app": "productpage",
          "service.istio.io/canonical-revision": "v1",
          "pod-template-hash": "65576bb7bf",
          "istio.io/rev": "default"
        },
        "ISTIO_PROXY_SHA": "istio-proxy:262253d9d066f8ef7ed82fd175c28b8f95acbec0",
        "NAME": "productpage-v1-65576bb7bf-4mzsp",
        "NAMESPACE": "default",
        "EXCHANGE_KEYS": "NAME,NAMESPACE,INSTANCE_IPS,LABELS,OWNER,PLATFORM_METADATA,WORKLOAD_NAME,MESH_ID,SERVICE_ACCOUNT,CLUSTER_ID",
        "INSTANCE_IPS": "172.18.0.8",
        "POD_PORTS": "[{\"containerPort\":9080,\"protocol\":\"TCP\"}]",
        "INTERCEPTION_MODE": "REDIRECT",
        "SERVICE_ACCOUNT": "bookinfo-productpage",
        "MESH_ID": "cluster.local",
        "SDS": "true",
        "WORKLOAD_NAME": "productpage-v1",
        "OWNER": "kubernetes://apis/apps/v1/namespaces/default/deployments/productpage-v1",
        "ISTIO_VERSION": "1.7.1"
      },
      "locality": {
        "region": "us-central1",
        "zone": "us-central1-a"
      }
    },
    "logName": "http_envoy_accesslog"
  }
}
