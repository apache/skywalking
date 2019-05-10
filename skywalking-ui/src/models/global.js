/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import moment from 'moment-timezone';
import { exec } from '../services/graphql';
import { generateDuration } from '../utils/time';

const noticeQuery = `
  query Notice($duration:Duration!){
    applicationAlarmList: getAlarm(scope: Service, duration: $duration, paging: { pageNum: 1, pageSize: 5, needTotal: true }) {
      msgs {
        key: id
        message
        startTime
      }
      total
    }
    serverAlarmList: getAlarm(scope: ServiceInstance, duration: $duration, paging: { pageNum: 1, pageSize: 5, needTotal: true }) {
      msgs {
        key: id
        message
        startTime
      }
      total
    }
  }
`;

export default {
  namespace: 'global',

  state: {
    collapsed: true,
    isMonitor: false,
    notices: {
      applicationAlarmList: {
        msgs: [],
        total: 0,
      },
      serverAlarmList: {
        msgs: [],
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
    zone: moment.tz.guess(),
  },

  effects: {
    *fetchNotice({ payload: { variables } }, { call, put }) {
      const response = yield call(exec, { query: noticeQuery, variables });
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
          raw: value.raw,
        },
        globalVariables: { duration: value.input },
      };
    },
    reloadDuration(state) {
      const { duration } = state;
      if (!duration.collapsed) {
        return state;
      }
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
    toggleMonitorHeader(state, { payload }) {
      return {
        ...state,
        isMonitor: payload,
      };
    },
    changeTimezone(state, { payload }) {
      moment.tz.setDefault(payload);
      return {
        ...state,
        zone: payload,
      };
    },
  },
  subscriptions: {
    setup({ history, dispatch }) {
      // Subscribe history(url) change, trigger `load` action if pathname is `/`
      return history.listen(({ pathname, search }) => {
        if (typeof window.ga !== 'undefined') {
          window.ga('send', 'pageview', pathname + search);
        }
        if (pathname && pathname.startsWith('/monitor')) {
          dispatch({
            type: 'toggleMonitorHeader',
            payload: true,
          });
        } else {
          dispatch({
            type: 'toggleMonitorHeader',
            payload: false,
          });
        }
      });
    },
  },
};
