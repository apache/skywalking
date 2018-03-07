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


import mockjs from 'mockjs';

export default {
  getNoticeAlarm(req, res) {
    return res.json(mockjs.mock(
      {
        data: {
          applicationAlarmList: {
            'items|5': [{
              'key|+1': 1,
              title: '@name',
              startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
              'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
              alarmType: 'APPLICATION',
            }],
            total: '@natural(5, 50)',
          },
          serverAlarmList: {
            'items|5': [{
              'key|+1': 1,
              title: '@name',
              startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
              'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
              alarmType: 'SERVER',
            }],
            total: '@natural(5, 50)',
          },
        },
      }
    ));
  },
  getAlarm(req, res) {
    const { variables: { alarmType } } = req.body;
    switch (alarmType) {
      case 'APPLICATION':
        return res.json(mockjs.mock(
          {
            data: {
              loadAlarmList: {
                'items|10': [{
                  'key|+1': 1,
                  title: '@name',
                  content: '@paragraph(1)',
                  startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
                  'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
                  alarmType: 'APPLICATION',
                }],
                total: '@natural(5, 50)',
              },
            },
          }
        ));
      case 'SERVER':
        return res.json(mockjs.mock(
          {
            data: {
              loadAlarmList: {
                'items|10': [{
                  'key|+1': 1,
                  title: '@name',
                  content: '@paragraph(1)',
                  startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
                  'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
                  alarmType: 'SERVER',
                }],
                total: '@natural(5, 50)',
              },
            },
          }
        ));
      case 'SERVICE':
        return res.json(mockjs.mock(
          {
            data: {
              loadAlarmList: {
                'items|10': [{
                  'key|+1': 1,
                  title: '@name',
                  content: '@paragraph(1)',
                  startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
                  'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
                  alarmType: 'SERVICE',
                }],
                total: '@natural(5, 50)',
              },
            },
          }
        ));
      default:
        return null;
    }
  },
};
