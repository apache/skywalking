import { query } from '../services/graphql';

export default {
  namespace: 'topology',
  state: {
    getClusterTopology: {
      nodes: [],
      calls: [],
    },
  },
  effects: {
    *fetch({ payload }, { call, put }) {
      const response = yield call(query, 'topology', payload);
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
