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


import { generateModal, saveOptionsInState } from '../utils/models';
import { query } from '../services/graphql';

const optionsQuery = `
  query ApplicationOption($duration: Duration!) {
    applicationId: getAllApplication(duration: $duration) {
      key: id
      label: name
    }
  }
`;

const dataQuery = `
  query Service($serviceId: ID!, $duration: Duration!, $traceCondition: TraceQueryCondition!) {
    getServiceResponseTimeTrend(serviceId: $serviceId, duration: $duration) {
      trendList
    }
    getServiceThroughputTrend(serviceId: $serviceId, duration: $duration) {
      trendList
    }
    getServiceSLATrend(serviceId: $serviceId, duration: $duration) {
      trendList
    }
    getTrace(condition: $traceCondition) {
      traces {
        key: segmentId
        operationName
        duration
        start
        isError
        traceIds
      }
      total
    }
    getServiceTopology(serviceId: $serviceId, duration: $duration) {
      nodes {
        id
        name
        type
        ... on ServiceNode {
          sla
          calls
          numOfServiceAlarm
        }
      }
      calls {
        source
        target
        isAlert
        callType
        cpm
        avgResponseTime
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
      applicationCode
      startTime
      endTime
      operationName
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

export default generateModal({
  namespace: 'service',
  state: {
    getServiceResponseTimeTrend: {
      trendList: [],
    },
    getServiceThroughputTrend: {
      trendList: [],
    },
    getServiceSLATrend: {
      trendList: [],
    },
    getServiceTopology: {
      nodes: [],
      calls: [],
    },
    getTrace: {
      traces: [],
      total: 0,
    },
  },
  dataQuery,
  optionsQuery,
  effects: {
    *fetchSpans({ payload }, { call, put }) {
      const response = yield call(query, 'spans', { query: spanQuery, variables: payload.variables });
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
    saveAppInfo(preState, { payload: allOptions }) {
      const rawState = saveOptionsInState(null, preState, { payload: allOptions });
      const { data } = rawState;
      if (data.appInfo) {
        return rawState;
      }
      const { variables: { values } } = rawState;
      return {
        ...rawState,
        data: {
          ...data,
          appInfo: { applicationId: values.applicationId },
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
        if (pathname === '/monitor/service' && state) {
          dispatch({
            type: 'saveVariables',
            payload: {
              values: {
                serviceId: state.key,
              },
              labels: {
                serviceId: state.label,
              },
            },
          });
        }
      });
    },
  },
});
