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


import { query403, query401, query404, query500 } from '../services/error';

export default {
  namespace: 'error',

  state: {
    error: '',
    isloading: false,
  },

  effects: {
    *query403(_, { call, put }) {
      yield call(query403);
      yield put({
        type: 'trigger',
        payload: '403',
      });
    },
    *query401(_, { call, put }) {
      yield call(query401);
      yield put({
        type: 'trigger',
        payload: '401',
      });
    },
    *query500(_, { call, put }) {
      yield call(query500);
      yield put({
        type: 'trigger',
        payload: '500',
      });
    },
    *query404(_, { call, put }) {
      yield call(query404);
      yield put({
        type: 'trigger',
        payload: '404',
      });
    },
  },

  reducers: {
    trigger(state, action) {
      return {
        error: action.payload,
      };
    },
  },
};
