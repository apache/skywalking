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
import { exec } from '../services/graphql';

const metricQuery = `
  query TopologyMetric($duration: Duration!, $ids: [ID!]!) {
    sla: getValues(metric: {
      name: "service_sla"
      ids: $ids
    }, duration: $duration) {
      values {
        id
        value
      }
    }
    nodeCpm: getValues(metric: {
      name: "service_cpm"
      ids: $ids
    }, duration: $duration) {
      values {
        id
        value
      }
    }
    nodeLatency: getValues(metric: {
      name: "service_resp_time"
      ids: $ids
    }, duration: $duration) {
      values {
        id
        value
      }
    }
  }
`;

const serverMetricQuery = `
query TopologyServerMetric($duration: Duration!, $idsS: [ID!]!) {
  cpmS: getValues(metric: {
    name: "service_relation_server_cpm"
    ids: $idsS
  }, duration: $duration) {
    values {
      id
      value
    }
  }
  latencyS: getValues(metric: {
    name: "service_relation_client_resp_time"
    ids: $idsS
  }, duration: $duration) {
    values {
      id
      value
    }
  }
}
`

const clientMetricQuery = `
query TopologyClientMetric($duration: Duration!, $idsC: [ID!]!) {
  cpmC: getValues(metric: {
    name: "service_relation_client_cpm"
    ids: $idsC
  }, duration: $duration) {
    values {
      id
      value
    }
  }
  latencyC: getValues(metric: {
    name: "service_relation_client_resp_time"
    ids: $idsC
  }, duration: $duration) {
    values {
      id
      value
    }
  }
}
`

export default base({
  namespace: 'topology',
  state: {
    getGlobalTopology: {
      nodes: [],
      calls: [],
    },
    metrics: {
      sla: {
        values: [],
      },
      nodeCpm: {
        values: [],
      },
      nodeLatency: {
        values: [],
      },
      cpm: {
        values: [],
      },
      latency: {
        values: [],
      },
    },
  },
  varState: {
    latencyRange: [100, 500],
  },
  dataQuery: `
    query Topology($duration: Duration!) {
      getGlobalTopology(duration: $duration) {
        nodes {
          id
          name
          type
          isReal
        }
        calls {
          id
          source
          target
          callType
          detectPoint
        }
      }
    }
  `,
  effects: {
    *fetchMetrics({ payload }, { call, put }) {
      const { ids, idsS, idsC, duration } = payload.variables;
      const { data = {} } = yield call(exec, { query: metricQuery, variables: { ids, duration } });
      let metrics = { ...data };
      if (idsS && idsS.length > 0) {
        const { data: sData = {}  } = yield call(exec, { query: serverMetricQuery, variables: { idsS, duration } });
        metrics = { ...metrics, ...sData };
      }
      if (idsC && idsC.length > 0) {
        const { data: cData = {}  } = yield call(exec, { query: clientMetricQuery, variables: { idsC, duration } });
        metrics = { ...metrics, ...cData };
      }
      const { cpmS = { values:[] }, cpmC = { values:[] }, latencyS = { values:[] }, latencyC = { values:[] } } = metrics;
      metrics = {
        ...metrics,
        cpm: {
          values: cpmS.values.concat(cpmC.values),
        },
        latency: {
          values: latencyS.values.concat(latencyC.values),
        },
      }
      yield put({
        type: 'saveData',
        payload: {
          metrics,
        },
      });
    },
  },
  reducers: {
    filterApplication(preState, { payload: { aa } }) {
      const { variables } = preState;
      if (aa.length < 1) {
        const newVariables = { ...variables };
        delete newVariables.appRegExps;
        delete newVariables.appFilters;
        return {
          ...preState,
          variables: newVariables,
        };
      }
      return {
        ...preState,
        variables: {
          ...variables,
          appFilters: aa,
          appRegExps: aa.map((a) => {
            try {
              return new RegExp(a, 'i');
            } catch (e) {
              return null;
            }
          }),
        },
      };
    },
    setLatencyStyleRange(preState, { payload: { latencyRange } }) {
      const { variables } = preState;
      return {
        ...preState,
        variables: {
          ...variables,
          latencyRange,
        },
      };
    },
  },
});
