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


import { base } from '../utils/models';

const dataQuery = `
  query Alarm($keyword: String, $scope: Scope, $duration:Duration!, $paging: Pagination!){
    getAlarm(keyword: $keyword, scope: $scope, duration: $duration, paging: $paging) {
      msgs {
        key: id
        message
        startTime
      }
      total
    }
  }
`;

export default base({
  namespace: 'alarm',
  state: {
    serviceAlarmList: {
      msgs: [],
      total: 0,
    },
    serviceInstanceAlarmList: {
      msgs: [],
      total: 0,
    },
    endpointAlarmList: {
      msgs: [],
      total: 0,
    },
  },
  dataQuery,
  reducers: {
    saveServiceAlarmList(preState, { payload }) {
      if (!payload) {
        return preState;
      }
      const { getAlarm } = payload;
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          serviceAlarmList: getAlarm,
        },
      };
    },
    saveServiceInstanceAlarmList(preState, { payload }) {
      if (!payload) {
        return preState;
      }
      const { getAlarm } = payload;
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          serviceInstanceAlarmList: getAlarm,
        },
      };
    },
    saveEndpointAlarmList(preState, { payload }) {
      if (!payload) {
        return preState;
      }
      const { getAlarm } = payload;
      const { data } = preState;
      return {
        ...preState,
        data: {
          ...data,
          endpointAlarmList: getAlarm,
        },
      };
    },
  },
  subscriptions: {
    setup({ history, dispatch }) {
      return history.listen(({ pathname, state }) => {
        if (pathname === '/monitor/alarm' && state) {
          dispatch({
            type: 'saveVariables',
            payload: { values: {
              scope: state.type,
            } },
          });
        }
      });
    },
  },
});
