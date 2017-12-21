// import { xxx } from '../services/xxx';
export default {
  namespace: "service",
  state: {},
  effects: {
    *fetch({ payload }, { call, put }) {
    },
  },
  reducers: {
    save(state, action) {
      return {
        ...state,
      };
    },
  },
};

