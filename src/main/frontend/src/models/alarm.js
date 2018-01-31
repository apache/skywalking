import { generateModal } from '../utils/utils';

const dataQuery = `
  query Alarm($keyword: String, $alarmType: AlarmType, $duration:Duration!, $paging: Pagination!){
    loadAlarmList(keyword: $keyword, alarmType: $alarmType, duration: $duration, paging: $paging)
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
    saveApplicationAlarmList(preState, { payload: { loadAlarmList } }) {
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          applicationAlarmList: loadAlarmList,
        },
      };
    },
    saveServerAlarmList(preState, { payload: { loadAlarmList } }) {
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          serverAlarmList: loadAlarmList,
        },
      };
    },
    saveServiceAlarmList(preState, { payload: { loadAlarmList } }) {
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
});
