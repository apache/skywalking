import { query } from '../services/graphql';

export default {
  namespace: 'server',
  state: {
    searchServer: [],
    getServerResponseTimeTrend: {
      trendList: [],
    },
    getServerTPSTrend: {
      trendList: [],
    },
    getCPUTrend: {
      cost: [],
    },
    getMemoryTrend: {
      heap: [],
      maxHeap: [],
      noheap: [],
      maxNoheap: [],
    },
    getGCTrend: {
      youngGC: [],
      oldGC: [],
    },
  },
  effects: {
    *fetch({ payload }, { call, put }) {
      const response = yield call(query, 'server', payload);
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
