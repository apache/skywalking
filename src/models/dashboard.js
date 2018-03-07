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


import { generateModal } from '../utils/models';

export default generateModal({
  namespace: 'dashboard',
  state: {
    getClusterBrief: {
      numOfApplication: 0,
      numOfService: 0,
      numOfDatabase: 0,
      numOfCache: 0,
      numOfMQ: 0,
    },
    getAlarmTrend: {
      numOfAlarmRate: [],
    },
    getConjecturalApps: {
      apps: [],
    },
    getTopNSlowService: [],
    getTopNServerThroughput: [],
  },
  dataQuery: `
    query Dashboard($duration: Duration!) {
      getClusterBrief(duration: $duration) {
        numOfApplication
        numOfService
        numOfDatabase
        numOfCache
        numOfMQ
      }
      getAlarmTrend(duration: $duration) {
        numOfAlarmRate
      }
      getConjecturalApps(duration: $duration) {
        apps {
          name
          num
        }
      }
      getTopNSlowService(duration: $duration, topN: 10) {
        key: id
        name
        avgResponseTime
      }
      getTopNApplicationThroughput(duration: $duration, topN: 10) {
        key: applicationId
        applicationCode
        callsPerSec
      }
    }
  `,
});
