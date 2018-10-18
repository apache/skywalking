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
  getDashboard(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          getClusterBrief: {
            'numOfApplication|1-100': 1,
            'numOfService|1-100': 1,
            'numOfDatabase|1-100': 1,
            'numOfCache|1-100': 1,
            'numOfMQ|1-100': 1,
          },
          getAlarmTrend: {
            'numOfAlarmRate|60': ['@natural(0, 9999)'],
          },
          getConjecturalApps: {
            'apps|3-5': [{ 'name|1': ['Oracle', 'MySQL', 'ActiveMQ', 'Redis', 'Memcache', 'SQLServer'], num: '@natural(1, 20)' }],
          },
          getThermodynamic: {
            nodes: () => {
              const result = [];
              for (let i = 0; i < 61; i += 1) {
                for (let j = 0; j < 41; j += 1) {
                  result.push([i, j, mockjs.Random.natural(0, 999)]);
                }
              }
              return result;
            },
            responseTimeStep: 50,
          },
          'getTopNSlowService|10': [{ service: { 'key|+1': 1, label: '@url', 'applicationId|+1': 1, applicationName: '@name' }, 'value|200-1000': 1 }],
          'getTopNApplicationThroughput|10': [{ 'key|+1': 1, label: '@name', 'value|100-10000': 1 }],
        },
      }
    ));
  },
};
