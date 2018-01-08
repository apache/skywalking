import { query } from '../services/graphql';

export default {
  namespace: 'service',
  state: {
    searchService: [],
    getServiceResponseTimeTrend: {
      trendList: [],
    },
    getServiceTPSTrend: {
      trendList: [],
    },
    getServiceSLATrend: {
      trendList: [],
    },
  },
  effects: {
    *fetch({ payload }, { call, put }) {
      const response = yield call(query, 'service', payload);
      yield put({
        type: 'save',
        payload: response,
      });
    },
  },

  reducers: {
    save(state, action) {
      return {
        ...state,
        ...action.payload.data,
      };
    },
  },
};
