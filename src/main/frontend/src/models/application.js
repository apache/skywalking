import { query } from '../services/graphql';

export default {
  namespace: 'application',
  state: {
    getAllApplication: [],
    getSlowService: [],
    getServerThroughput: [],
  },
  effects: {
    *fetch({ payload }, { call, put }) {
      const response = yield call(query, 'application', payload);
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
