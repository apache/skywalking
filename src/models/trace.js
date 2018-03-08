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


import { query } from '../services/graphql';
import { generateModal } from '../utils/models';

const optionsQuery = `
  query ApplicationOption($duration: Duration!) {
    applicationId: getAllApplication(duration: $duration) {
      key: id
      label: name
    }
  }
`;

const dataQuery = `
  query BasicTraces($condition: TraceQueryCondition) {
    queryBasicTraces(condition: $condition) {
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
  namespace: 'trace',
  state: {
    queryBasicTraces: {
      traces: [],
      pagination: {
        current: 1,
        pageSize: 10,
        total: 0,
      },
    },
  },
  optionsQuery,
  defaultOption: {
    applicationId: {
      label: 'All Application',
    },
  },
  dataQuery,
  effects: {
    *fetchSpans({ payload }, { call, put }) {
      const response = yield call(query, 'spans', { query: spanQuery, variables: payload.variables });
      yield put({
        type: 'saveSpans',
        payload: response,
        key: payload.key,
        traceId: payload.variables.traceId,
      });
    },
  },
  reducers: {
    saveSpans(state, action) {
      const { key, traceId } = action;
      const { queryTrace: { spans } } = action.payload.data;
      const { data: { queryBasicTraces: { traces } } } = state;
      const trace = traces.find(t => t.key === key);
      const { spansContainer = {} } = trace;
      spansContainer[traceId] = spans;
      trace.spansContainer = spansContainer;
      return {
        ...state,
      };
    },
  },
});
