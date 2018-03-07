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
  getTopology(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          getClusterTopology: () => {
            const application = mockjs.mock({
              'nodes|2': [
                {
                  'id|+1': 1,
                  name: '@name',
                  'type|1': ['DUBBO', 'tomcat', 'SPRINGMVC'],
                  'callsPerSec|1000-2000': 1,
                  'sla|1-100.1-2': 1,
                  'apdex|0.2': 1,
                  'avgResponseTime|500-1000': 1,
                  'isAlarm|1': true,
                  'numOfServer|1-100': 1,
                  'numOfServerAlarm|1-100': 1,
                  'numOfServiceAlarm|1-100': 1,
                },
              ],
            });
            const users = mockjs.mock({
              nodes: [
                {
                  id: 100,
                  name: 'User',
                  type: 'USER',
                },
              ],
            });
            const resources = mockjs.mock({
              'nodes|5': [
                {
                  'id|+1': 200,
                  name: '@name',
                  'type|1': ['Oracle', 'MYSQL', 'REDIS'],
                },
              ],
            });
            const nodes = users.nodes.concat(application.nodes, resources.nodes);
            const userConnectApplication = mockjs.mock({
              calls: [
                {
                  source: 100,
                  target: 1,
                  'isAlert|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'avgResponseTime|500-5000': 1,
                },
                {
                  source: 1,
                  target: 2,
                  'isAlert|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'avgResponseTime|500-5000': 1,
                },
                {
                  source: 1,
                  target: 200,
                  'isAlert|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'avgResponseTime|500-5000': 1,
                },
                {
                  source: 1,
                  target: 201,
                  'isAlert|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'avgResponseTime|500-5000': 1,
                },
                {
                  source: 2,
                  target: 202,
                  'isAlert|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'avgResponseTime|500-5000': 1,
                },
                {
                  source: 2,
                  target: 203,
                  'isAlert|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'avgResponseTime|500-5000': 1,
                },
                {
                  source: 2,
                  target: 204,
                  'isAlert|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'avgResponseTime|500-5000': 1,
                },
              ],
            });
            return {
              nodes,
              calls: userConnectApplication.calls,
            };
          },
        },
      }
    ));
  },
};
