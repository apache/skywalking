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
            "id": "router~10.44.2.56~istio-ingressgateway-699c7dc774-hjxq5.istio-system~istio-system.svc.cluster.local",
            "cluster": "istio-ingressgateway",
            "metadata": {
                "CONFIG_NAMESPACE": "istio-system",
                "ISTIO_META_INSTANCE_IPS": "10.44.2.56,10.44.2.56,fe80::9ca5:e5ff:fede:6414",
                "ISTIO_PROXY_SHA": "istio-proxy:55c80965eab994e6bfa2227e3942fa89928d0d70",
                "ISTIO_PROXY_VERSION": "1.1.0",
                "ISTIO_VERSION": "1.0-dev",
                "POD_NAME": "istio-ingressgateway-699c7dc774-hjxq5",
                "ROUTER_MODE": "sni-dnat",
                "istio": "sidecar"
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
                            "address": "10.138.0.14",
                            "portValue": 51489
                        }
                    },
                    "downstreamLocalAddress": {
                        "socketAddress": {
                            "address": "10.44.2.56",
                            "portValue": 80
                        }
                    },
                    "startTime": "2019-04-13T03:59:53.687224601Z",
                    "timeToLastRxByte": "0.000031206s",
                    "timeToFirstUpstreamTxByte": "0.000869250s",
                    "timeToLastUpstreamTxByte": "0.000881276s",
                    "timeToFirstUpstreamRxByte": "1.010010710s",
                    "timeToLastUpstreamRxByte": "1.010423815s",
                    "timeToFirstDownstreamTxByte": "1.010053396s",
                    "timeToLastDownstreamTxByte": "1.010432910s",
                    "upstreamRemoteAddress": {
                        "socketAddress": {
                            "address": "10.44.2.54",
                            "portValue": 9080
                        }
                    },
                    "upstreamCluster": "outbound|9080||productpage.default.svc.cluster.local"
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
                    "requestHeadersBytes": "1038"
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
