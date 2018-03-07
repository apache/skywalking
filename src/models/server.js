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

const dataQuery = `
  query Application($serverId: ID!, $duration: Duration!) {
    getServerResponseTimeTrend(serverId: $serverId, duration: $duration) {
      trendList
    }
    getServerTPSTrend(serverId: $serverId, duration: $duration) {
      trendList
    }
    getCPUTrend(serverId: $serverId, duration: $duration) {
      cost
    }
    getGCTrend(serverId: $serverId, duration: $duration) {
      youngGC
      oldGC
    }
    getMemoryTrend(serverId: $serverId, duration: $duration) {
      heap
      maxHeap
      noheap
      maxNoheap
    }
  }
`;

export default generateModal({
  namespace: 'server',
  state: {
    serverInfo: {},
    getServerResponseTimeTrend: {
      trendList: [],
    },
    getServerTPSTrend: {
      trendList: [],
    },
    getCPUTrend: {
      cost: [],
    },
    getMemoryTrend: {
      heap: [],
      maxHeap: [],
      noheap: [],
      maxNoheap: [],
    },
    getGCTrend: {
      youngGC: [],
      oldGC: [],
    },
  },
  dataQuery,
});
