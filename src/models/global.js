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
