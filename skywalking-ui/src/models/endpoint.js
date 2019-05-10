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


import { base, saveOptionsInState } from '../utils/models';
import { exec } from '../services/graphql';

const optionsQuery = `
  query ServiceOption($duration: Duration!) {
    serviceId: getAllServices(duration: $duration) {
      key: id
      label: name
    }
  }
`;

const dataQuery = `
  query Endpoint($endpointId: ID!, $duration: Duration!, $traceCondition: TraceQueryCondition!) {
    getEndpointResponseTimeTrend: getLinearIntValues(metric: {
      name: "endpoint_avg"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    getEndpointThroughputTrend: getLinearIntValues(metric: {
      name: "endpoint_cpm"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    getEndpointSLATrend: getLinearIntValues(metric: {
      name: "endpoint_sla"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    queryBasicTraces(condition: $traceCondition) {
      traces {
        key: segmentId
        endpointNames
        duration
        start
        isError
        traceIds
      }
      total
    }
    getP99: getLinearIntValues(metric: {
      name: "endpoint_p99"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP95: getLinearIntValues(metric: {
      name: "endpoint_p95"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP90: getLinearIntValues(metric: {
      name: "endpoint_p90"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP75: getLinearIntValues(metric: {
      name: "endpoint_p75"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP50: getLinearIntValues(metric: {
      name: "endpoint_p50"
      id: $endpointId
    }, duration: $duration) {
      values {
        value
      }
    }
    getEndpointTopology(endpointId: $endpointId, duration: $duration) {
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
`;


const spanQuery = `query Spans($traceId: ID!) {
  queryTrace(traceId: $traceId) {
    spans {
      traceId
      segmentId
      spanId
      parentSpanId
      refs {
        traceId
        parentSegmentId
        parentSpanId
        type
      }
      serviceCode
      startTime
      endTime
      endpointName
      type
      peer
      component
      isError
      layer
      tags {
        key
        value
      }
      logs {
        time
        data {
          key
          value
        }
      }
    }
  }
}`;

const metricQuery = `
  query TopologyMetric($duration: Duration!, $idsS: [ID!]!) {
    cpm: getValues(metric: {
      name: "endpoint_relation_cpm"
      ids: $idsS
    }, duration: $duration) {
      values {
        id
        value
      }
    }
  }
`;

const infoQuery = `
  query Info($endpointId: ID!) {
    endpointInfo: getEndpointInfo(endpointId: $endpointId) {
      key: id
      label: name
      serviceId
      serviceName
    }
  }
`;
export default base({
  namespace: 'endpoint',
  state: {
    getEndpointResponseTimeTrend: {
      values: [],
    },
    getEndpointThroughputTrend: {
      values: [],
    },
    getEndpointSLATrend: {
      values: [],
    },
    getEndpointTopology: {
      nodes: [],
      calls: [],
    },
    metrics: {
      cpm: {
        values: [],
      },
    },
    queryBasicTraces: {
      traces: [],
      total: 0,
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
  },
  dataQuery,
  optionsQuery,
  effects: {
    *fetchInfo({ payload }, { call, put }) {
      const response = yield call(exec, { query: infoQuery, variables: payload.variables });
      const { data } = response;
      if (!data.endpointInfo) {
        return;
      }
      const { endpointInfo } = data;
      yield put({
        type: 'saveVariables',
        payload: {
          values: {
            endpointId: endpointInfo.key,
            serviceId: endpointInfo.serviceId,
          },
          labels: {
            endpointId: endpointInfo.label,
            serviceId: endpointInfo.serviceName,
          },
        },
      });
      yield put({
        type: 'saveData',
        payload: {
          serviceInfo: { serviceId: endpointInfo.serviceId },
          endpointInfo,
        },
      });
    },
    *fetchSpans({ payload }, { call, put }) {
      const response = yield call(exec, { query: spanQuery, variables: payload.variables });
      yield put({
        type: 'saveSpans',
        payload: response,
        traceId: payload.variables.traceId,
      });
    },
    *fetchMetrics({ payload }, { call, put }) {
      const response = yield call(exec, { query: metricQuery, variables: payload.variables });
      if (!response.data) {
        return;
      }
      const { cpm } = response.data;
      yield put({
        type: 'saveData',
        payload: {
          metrics: {
            cpm,
          },
        },
      });
    },
  },
  reducers: {
    saveSpans(state, { payload, traceId }) {
      const { data } = state;
      return {
        ...state,
        data: {
          ...data,
          queryTrace: payload.data.queryTrace,
          currentTraceId: traceId,
          showTimeline: true,
        },
      };
    },
    saveServiceInfo(preState, { payload: allOptions }) {
      const rawState = saveOptionsInState(null, preState, { payload: allOptions });
      const { data } = rawState;
      if (data.serviceInfo) {
        return rawState;
      }
      const { variables: { values } } = rawState;
      if (!values.serviceId) {
        return rawState;
      }
      return {
        ...rawState,
        data: {
          ...data,
          serviceInfo: { serviceId: values.serviceId },
        },
      };
    },
    hideTimeline(state) {
      const { data } = state;
      return {
        ...state,
        data: {
          ...data,
          showTimeline: false,
        },
      };
    },
  },
  subscriptions: {
    setup({ history, dispatch }) {
      return history.listen(({ pathname, state }) => {
        if (pathname === '/monitor/endpoint' && state) {
          dispatch({
            type: 'fetchInfo',
            payload: {
              variables: {
                endpointId: state.key,
              },
            },
          });
        }
      });
    },
  },
});
