/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import { base } from '../utils/models';

export default base({
  namespace: 'dashboard',
  state: {
    getGlobalBrief: {
      numOfService: 0,
      numOfEndpoint: 0,
      numOfDatabase: 0,
      numOfCache: 0,
      numOfMQ: 0,
    },
    getThermodynamic: {
      nodes: [],
      responseTimeStep: 0,
    },
    getP99: {
      values: [],
    },
    getP95: {
      values: [],
    },
    getP90: {
      values: [],
    },
    getP75: {
      values: [],
    },
    getP50: {
      values: [],
    },
    getTopNSlowEndpoint: [],
    getTopNServiceThroughput: [],
  },
  dataQuery: `
    query Dashboard($duration: Duration!) {
      getGlobalBrief(duration: $duration) {
        numOfService
        numOfEndpoint
        numOfDatabase
        numOfCache
        numOfMQ
      }
      getThermodynamic(duration: $duration, metric: {
        name: "all_heatmap"
      }) {
        nodes
        responseTimeStep: axisYStep
      }
      getTopNSlowEndpoint: getAllEndpointTopN(
        duration: $duration,
        name: "endpoint_avg",
        topN: 10,
        order: DES
      ) {
        key: id
        label: name
        value
      }
      getTopNServiceThroughput: getServiceTopN(
        duration: $duration,
        name: "service_cpm",
        topN: 10,
        order: DES
      ) {
        key: id
        label: name
        value
      }
      getP99: getLinearIntValues(metric: {
        name: "all_p99"
      }, duration: $duration) {
        values {
          value
        }
      }
      getP95: getLinearIntValues(metric: {
        name: "all_p95"
      }, duration: $duration) {
        values {
          value
        }
      }
      getP90: getLinearIntValues(metric: {
        name: "all_p90"
      }, duration: $duration) {
        values {
          value
        }
      }
      getP75: getLinearIntValues(metric: {
        name: "all_p75"
      }, duration: $duration) {
        values {
          value
        }
      }
      getP50: getLinearIntValues(metric: {
        name: "all_p50"
      }, duration: $duration) {
        values {
          value
        }
      }
    }
  `,
});
