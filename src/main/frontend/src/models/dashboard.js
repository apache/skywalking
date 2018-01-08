import { query } from '../services/graphql';

export default {
  namespace: 'dashboard',
  state: {
    getClusterBrief: {
      numOfApplication: 0,
      numOfService: 0,
      numOfDatabase: 0,
      numOfCache: 0,
      numOfMQ: 0,
    },
    getAlarmTrend: {
      numOfAlarmRate: [],
    },
    getConjecturalApps: {
      apps: [],
    },
    getTopNSlowService: [],
    getTopNServerThroughput: [],
  },
  effects: {
    *fetch({ payload }, { call, put }) {
      const response = yield call(query, 'dashboard', payload);
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
