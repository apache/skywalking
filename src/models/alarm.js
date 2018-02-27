import { generateModal } from '../utils/models';

const dataQuery = `
  query Alarm($keyword: String, $alarmType: AlarmType, $duration:Duration!, $paging: Pagination!){
    loadAlarmList(keyword: $keyword, alarmType: $alarmType, duration: $duration, paging: $paging) {
      items {
        key: id
        title
        content
        startTime
        causeType
      }
      total
    }
  }
`;

export default generateModal({
  namespace: 'alarm',
  state: {
    applicationAlarmList: {
      items: [],
      total: 0,
    },
    serverAlarmList: {
      items: [],
      total: 0,
    },
    serviceAlarmList: {
      items: [],
      total: 0,
    },
  },
  dataQuery,
  reducers: {
    saveApplicationAlarmList(preState, { payload }) {
      if (!payload) {
        return preState;
      }
      const { loadAlarmList } = payload;
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          applicationAlarmList: loadAlarmList,
        },
      };
    },
    saveServerAlarmList(preState, { payload }) {
      if (!payload) {
        return preState;
      }
      const { loadAlarmList } = payload;
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          serverAlarmList: loadAlarmList,
        },
      };
    },
    saveServiceAlarmList(preState, { payload }) {
      if (!payload) {
        return preState;
      }
      const { loadAlarmList } = payload;
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          serviceAlarmList: loadAlarmList,
        },
      };
    },
  },
  subscriptions: {
    setup({ history, dispatch }) {
      return history.listen(({ pathname, state }) => {
        if (pathname === '/alarm' && state) {
          dispatch({
            type: 'saveVariables',
            payload: { values: {
              alarmType: state.type.toUpperCase(),
            } },
          });
        }
      });
    },
  },
});
