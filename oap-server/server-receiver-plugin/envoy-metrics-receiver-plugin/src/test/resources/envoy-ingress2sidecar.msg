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
    // Mock identifier
    "identifier": {
            "node": {
                "id": "sidecar~10.44.2.54~product-v1-d66dcfdc5-kh6v7.default~default.svc.cluster.local",
                "cluster": "product.default",
                "metadata": {
                    "CONFIG_NAMESPACE": "default",
                    "INTERCEPTION_MODE": "REDIRECT",
                    "ISTIO_META_INSTANCE_IPS": "10.44.2.54,10.44.2.54,fe80::d8e8:b6ff:fed6:f857",
                    "ISTIO_PROXY_SHA": "istio-proxy:55c80965eab994e6bfa2227e3942fa89928d0d70",
                    "ISTIO_PROXY_VERSION": "1.1.0",
                    "ISTIO_VERSION": "1.0-dev",
                    "POD_NAME": "product-v1-d66dcfdc5-kh6v7",
                    "app": "product",
                    "istio": "sidecar",
                    "kubernetes.io/limit-ranger": "LimitRanger plugin set: cpu request for container istio-proxy; cpu request for container reviews",
                    "pod-template-hash": "822879871",
                    "version": "v1"
                },
                "locality": { }
            },
            "logName": "als"
        },
    // Real log sample
    "httpLogs": {
        "logEntry": [
            {
                "commonProperties": {
                    "downstreamRemoteAddress": {
                        "socketAddress": {
                            "address": "10.138.0.14",
                            "portValue": 0
                        }
                    },
                    "downstreamLocalAddress": {
                        "socketAddress": {
                            "address": "10.44.2.54",
                            "portValue": 9080
                        }
                    },
                    "startTime": "2019-04-13T03:59:53.688609181Z",
                    "timeToLastRxByte": "0.000081758s",
                    "timeToFirstUpstreamTxByte": "0.000789220s",
                    "timeToLastUpstreamTxByte": "0.000808326s",
                    "timeToFirstUpstreamRxByte": "1.008120501s",
                    "timeToLastUpstreamRxByte": "1.008369826s",
                    "timeToFirstDownstreamTxByte": "1.008242458s",
                    "timeToLastDownstreamTxByte": "1.008378251s",
                    "upstreamRemoteAddress": {
                        "socketAddress": {
                            "address": "127.0.0.1",
                            "portValue": 9080
                        }
                    },
                    "upstreamCluster": "inbound|9080|http|productpage.default.svc.cluster.local",
                    "metadata": {
                        "filterMetadata": {
                            "istio_authn": { }
                        }
                    }
                },
                "protocolVersion": "HTTP11",
                "request": {
                    "requestMethod": "GET",
                    "scheme": "http",
                    "authority": "35.227.162.132",
                    "path": "/productpage",
                    "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1 Safari/605.1.15",
                    "forwardedFor": "10.138.0.14",
                    "requestId": "0ac1feff-84ae-4d3a-8b15-890da2b194c5",
                    "requestHeadersBytes": "579"
                },
                "response": {
                    "responseCode": 200,
                    "responseHeadersBytes": "147",
                    "responseBodyBytes": "4415"
                }
            }
        ]
    }
}
