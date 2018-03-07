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

const optionsQuery = `
  query ApplicationOption($duration: Duration!) {
    applicationId: getAllApplication(duration: $duration) {
      key: id
      label: name
    }
  }
`;

const dataQuery = `
  query Application($applicationId: ID!, $duration: Duration!) {
    getSlowService(applicationId: $applicationId, duration: $duration, topN: 10) {
      key: id
      name
      avgResponseTime
    }
    getServerThroughput(applicationId: $applicationId, duration: $duration, topN: 10) {
      key: id
      name
      callsPerSec
    }
    getApplicationTopology(applicationId: $applicationId, duration: $duration) {
      nodes {
        id
        name
        type
        ... on ApplicationNode {
          sla
          callsPerSec
          avgResponseTime
          apdex
          isAlarm
          numOfServer
          numOfServerAlarm
          numOfServiceAlarm
        }
      }
      calls {
        source
        target
        isAlert
        callType
        callsPerSec
        avgResponseTime
      }
    }
  }
`;

export default generateModal({
  namespace: 'application',
  state: {
    allApplication: [],
    getSlowService: [],
    getServerThroughput: [],
    getApplicationTopology: {
      nodes: [],
      calls: [],
    },
  },
  optionsQuery,
  dataQuery,
});
