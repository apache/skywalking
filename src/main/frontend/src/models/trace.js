import { query } from '../services/graphql';

export default {
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
  effects: {
    *fetch({ payload, pagination }, { call, put }) {
      const response = yield call(query, 'trace', payload);
      yield put({
        type: 'save',
        payload: response,
        pagination,
      });
    },
    *fetchSpans({ payload }, { call, put }) {
      const response = yield call(query, 'spans', payload);
      yield put({
        type: 'saveSpans',
        payload: response,
        traceId: payload.variables.traceId,
      });
    },
  },

  reducers: {
    save(state, action) {
      const { pagination } = action;
      const { queryBasicTraces: { traces, total } } = action.payload.data;
      return {
        ...state,
        queryBasicTraces: {
          traces,
          pagination: {
            ...pagination,
            total,
          },
        },
      };
    },
    saveSpans(state, action) {
      const { traceId } = action;
      const { queryTrace: { spans } } = action.payload.data;
      const { queryBasicTraces: { traces } } = state;
      const trace = traces.find(t => t.traceId === traceId);
      trace.spans = spans;
      return {
        ...state,
      };
    },
  },
};
