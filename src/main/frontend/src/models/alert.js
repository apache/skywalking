import { query } from '../services/graphql';

export default {
  namespace: 'alert',
  state: {
    loadAlertList: [],
  },
  effects: {
    *fetch({ payload }, { call, put }) {
      const response = yield call(query, 'alert', payload);
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
