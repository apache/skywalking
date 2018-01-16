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
  },
};
