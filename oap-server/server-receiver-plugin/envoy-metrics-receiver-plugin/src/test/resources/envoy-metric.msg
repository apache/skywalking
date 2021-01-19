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

identifier {
    node {
      id: "ingress",
      cluster: "envoy-proxy",
      metadata {
        fields {
            key: "envoy"
            value {
              string_value: "isawesome"
            }
            key: "skywalking"
            value {
              string_value: "iscool"
            }
          }
      },
      locality {
        region: "ap-southeast-1"
        zone: "zone1"
        sub_zone: "subzone1"
      }
    }
}
envoy_metrics [
    {
        name: "cluster.service_stats.update_attempt",
        type: COUNTER
        metrics [
            {
              counter {
                value: 1
              },
              timestamp_ms: 1551781658343
            }
        ]
    },
    {
        name: "cluster.service_stats.membership_change",
        type: COUNTER
        metrics [
            {
              counter {
                value: 1
              },
              timestamp_ms: 1551781658343
            }
        ]
    },
    {
        name: "server.parent_connections",
        type: GAUGE
        metrics [
          {
            gauge {
              value: 50
            },
            timestamp_ms: 1551781658343
          }
        ]
    },
    {
        name: "server.total_connections",
        type: GAUGE
        metrics [
          {
            gauge {
              value: 14
            },
            timestamp_ms: 1551781658343
          }
        ]
    },
    {
        name: "server.memory_heap_size",
        type: GAUGE
        metrics [
          {
            gauge {
              value: 3145728
            },
            timestamp_ms: 1551781658343
          }
        ]
    }
]
