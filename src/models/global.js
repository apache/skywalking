import { queryNotices } from '../services/api';
import { generateDuration } from '../utils/time';

export default {
  namespace: 'global',

  state: {
    collapsed: false,
    notices: [],
    duration: {
      collapsed: true,
      display: {
        range: [],
      },
    },
    globalVariables: {},
  },

  effects: {
    *fetchNotices(_, { call, put }) {
      const data = yield call(queryNotices);
      yield put({
        type: 'saveNotices',
        payload: data,
      });
      yield put({
        type: 'user/changeNotifyCount',
        payload: data.length,
      });
    },
    *clearNotices({ payload }, { put, select }) {
      yield put({
        type: 'saveClearedNotices',
        payload,
      });
      const count = yield select(state => state.global.notices.length);
      yield put({
        type: 'user/changeNotifyCount',
        payload: count,
      });
    },
  },

  reducers: {
    changeDurationCollapsed(state, { payload }) {
      const { duration } = state;
      return {
        ...state,
        duration: {
          ...duration,
          collapsed: !payload,
        },
      };
    },
    changeDuration(state, { payload }) {
      const { duration } = state;
      const value = generateDuration(payload);
      return {
        ...state,
        duration: {
          ...duration,
          collapsed: true,
          selected: payload,
          display: value.display,
        },
        globalVariables: { duration: value.input },
      };
    },
    reloadDuration(state) {
      const { duration } = state;
      const { selected } = duration;
      const value = generateDuration(selected);
      return {
        ...state,
        duration: {
          ...duration,
          display: value.display,
        },
        globalVariables: { duration: value.input },
      };
    },
    changeLayoutCollapsed(state, { payload }) {
      return {
        ...state,
        collapsed: payload,
      };
    },
    saveNotices(state, { payload }) {
      return {
        ...state,
        notices: payload,
      };
    },
    saveClearedNotices(state, { payload }) {
      return {
        ...state,
        notices: state.notices.filter(item => item.type !== payload),
      };
    },
  },

  subscriptions: {
    setup({ history }) {
      // Subscribe history(url) change, trigger `load` action if pathname is `/`
      return history.listen(({ pathname, search }) => {
        if (typeof window.ga !== 'undefined') {
          window.ga('send', 'pageview', pathname + search);
        }
      });
    },
  },
};
