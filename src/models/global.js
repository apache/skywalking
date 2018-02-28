import { query } from '../services/graphql';
import { generateDuration } from '../utils/time';

const noticeQuery = `
  query Notice($duration:Duration!){
    applicationAlarmList: loadAlarmList(alarmType: APPLICATION, duration: $duration, paging: { pageNum: 1, pageSize: 5, needTotal: true }) {
      items {
        title
        startTime
        causeType
      }
      total
    }
    serverAlarmList: loadAlarmList(alarmType: SERVER, duration: $duration, paging: { pageNum: 1, pageSize: 5, needTotal: true }) {
      items {
        title
        startTime
        causeType
      }
      total
    }
  }
`;

export default {
  namespace: 'global',

  state: {
    collapsed: false,
    notices: {
      applicationAlarmList: {
        items: [],
        total: 0,
      },
      serverAlarmList: {
        items: [],
        total: 0,
      },
    },
    duration: {
      collapsed: true,
      display: {
        range: [],
      },
    },
    globalVariables: {},
  },

  effects: {
    *fetchNotice({ payload: { variables } }, { call, put }) {
      const response = yield call(query, 'notice', { query: noticeQuery, variables });
      yield put({
        type: 'saveNotice',
        payload: response.data,
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
    saveNotice(state, { payload }) {
      return {
        ...state,
        notices: {
          ...state.notices,
          ...payload,
        },
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
