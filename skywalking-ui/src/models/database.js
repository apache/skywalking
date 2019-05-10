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

const optionsQuery = `
  query DatabaseOption($duration: Duration!) {
    databaseId: getAllDatabases(duration: $duration) {
      key: id
      label: name
      type
    }
  }
`;

const TopNRecordsQuery = `
  query TopNRecords($condition: TopNRecordsCondition!) {
    getTopNRecords(condition: $condition) {
      statement 
      latency
      traceId
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

const dataQuery = `
  query Database($databaseId: ID!, $duration: Duration!) {
    getResponseTimeTrend: getLinearIntValues(metric: {
      name: "database_access_resp_time"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
    getThroughputTrend: getLinearIntValues(metric: {
      name: "database_access_cpm"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
    getSLATrend: getLinearIntValues(metric: {
      name: "database_access_sla"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP99: getLinearIntValues(metric: {
      name: "database_access_p99"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP95: getLinearIntValues(metric: {
      name: "database_access_p95"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP90: getLinearIntValues(metric: {
      name: "database_access_p90"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP75: getLinearIntValues(metric: {
      name: "database_access_p75"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
    getP50: getLinearIntValues(metric: {
      name: "database_access_p50"
      id: $databaseId
    }, duration: $duration) {
      values {
        value
      }
    }
  }
`;


export default base({
  namespace: 'database',
  state: {
    allDatabase: [],
    getResponseTimeTrend: {
      values: [],
    },
    getThroughputTrend: {
      values: [],
    },
    getSLATrend: {
      values: [],
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
    getTopNRecords: [],
  },
  optionsQuery,
  dataQuery,
  effects: {
    *fetchTraces({ payload }, { call, put }) {
      const { variables } = payload;
      const response = yield call(exec, { variables, query: TopNRecordsQuery });
      if (!response.data) {
        return;
      }
      yield put({
        type: 'saveTraces',
        payload: response.data,
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
    saveTraces(state, { payload }) {
      const { data } = state;
      return {
        ...state,
        data: {
          ...data,
          getTopNRecords: payload.getTopNRecords,
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
    saveDatabase(preState, { payload }) {
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          ...payload,
        },
      };
    },
  },
  subscriptions: {
    setup({ history, dispatch }) {
      return history.listen(({ pathname, state }) => {
        if (pathname === '/monitor/database' && state) {
          dispatch({
            type: 'saveVariables',
            payload: {
              values: {
                databaseId: `${state.key}`,
              },
              labels: {
                databaseId: state.label,
              },
            },
          });
        }
      });
    },
  },
});
