import { query } from '../services/graphql';
import { generateDuration } from '../utils/utils';

const noticeQuery = `
  query Notice($duration:Duration!){
    applicationAlarmList: loadAlarmList(alarmType: 'APPLICATION', duration: $duration, paging: { pageNum: 1, pageSize: 5, needTotal: true }) {
      items {
        title
        startTime
        causeType
      }
      total
    }
    serverAlarmList: loadAlarmList(alarmType: 'SERVER', duration: $duration, paging: { pageNum: 1, pageSize: 5, needTotal: true }) {
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
    fetchingNotices: false,
    globalVariables: { duration: {} },
  },

  effects: {
    *fetchNotice({ payload: { variables } }, { call, put }) {
      const response = yield call(query, 'notice', { query: noticeQuery, variables });
      yield put({
        type: 'saveNotice',
        payload: response.data,
      });
    },
    *clearNotices({ payload }, { put, select }) {
      const count = yield select(state => state.global.notices.length);
      yield put({
        type: 'user/changeNotifyCount',
        payload: count,
      });

      yield put({
        type: 'saveClearedNotices',
        payload,
      });
    },
  },

  reducers: {
    changeLayoutCollapsed(state, { payload }) {
      return {
        ...state,
        collapsed: payload,
      };
    },
    saveNotice(state, { payload }) {
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
    changeNoticeLoading(state, { payload }) {
      return {
        ...state,
        fetchingNotices: payload,
      };
    },
    changeSelectedTime(state, { payload }) {
      const duration = generateDuration(payload);
      return {
        ...state,
        selectedTime: payload,
        duration,
        isShowSelectTime: false,
        globalVariables: { duration: duration.input },
      };
    },
    toggleSelectTime(state) {
      return {
        ...state,
        isShowSelectTime: !state.isShowSelectTime,
      };
    },
    reload(state) {
      const { selectedTime } = state;
      const duration = generateDuration(selectedTime);
      return {
        ...state,
        duration,
        globalVariables: { duration: duration.input },
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
