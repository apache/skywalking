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
                "id": "sidecar~10.44.2.55~productpage-v1-d66dcfdc5-kh6v7.default~default.svc.cluster.local",
                "cluster": "productpage.default",
                "metadata": {
                    "CONFIG_NAMESPACE": "default",
                    "INTERCEPTION_MODE": "REDIRECT",
                    "ISTIO_META_INSTANCE_IPS": "10.44.2.55,10.44.2.55,fe80::d8e8:b6ff:fed6:f857",
                    "ISTIO_PROXY_SHA": "istio-proxy:55c80965eab994e6bfa2227e3942fa89928d0d70",
                    "ISTIO_PROXY_VERSION": "1.1.0",
                    "ISTIO_VERSION": "1.0-dev",
                    "POD_NAME": "productpage-v1-d66dcfdc5-kh6v7",
                    "app": "productpage",
                    "istio": "sidecar",
                    "kubernetes.io/limit-ranger": "LimitRanger plugin set: cpu request for container istio-proxy; cpu request for container reviews",
                    "pod-template-hash": "822879871",
                    "version": "v1"
                },
                "locality": { }
            },
            "logName": "als"
        },
        "httpLogs": {
            "logEntry": [
                {
                    "commonProperties": {
                        "downstreamRemoteAddress": {
                            "socketAddress": {
                                "address": "10.44.2.54",
                                "portValue": 58996
                            }
                        },
                        "downstreamLocalAddress": {
                            "socketAddress": {
                                "address": "10.47.247.180",
                                "portValue": 9080
                            }
                        },
                        "startTime": "2019-04-13T03:59:53.695750999Z",
                        "timeToLastRxByte": "0.000082339s",
                        "timeToFirstUpstreamTxByte": "0.002353100s",
                        "timeToLastUpstreamTxByte": "0.002362295s",
                        "timeToFirstUpstreamRxByte": "0.010500490s",
                        "timeToLastUpstreamRxByte": "0.010735195s",
                        "timeToFirstDownstreamTxByte": "0.010669993s",
                        "timeToLastDownstreamTxByte": "0.010745496s",
                        "upstreamRemoteAddress": {
                            "socketAddress": {
                                "address": "10.44.6.66",
                                "portValue": 9080
                            }
                        },
                        "upstreamCluster": "outbound|9080||details.default.svc.cluster.local"
                    },
                    "protocolVersion": "HTTP11",
                    "request": {
                        "requestMethod": "GET",
                        "scheme": "http",
                        "authority": "details:9080",
                        "path": "/details/0",
                        "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1 Safari/605.1.15",
                        "requestId": "0ac1feff-84ae-4d3a-8b15-890da2b194c5",
                        "requestHeadersBytes": "869"
                    },
                    "response": {
                        "responseCode": 200,
                        "responseHeadersBytes": "129",
                        "responseBodyBytes": "178"
                    }
                }
            ]
        }
    }
}
