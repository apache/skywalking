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
  searchServer(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'searchServer|5': [
            {
              'key|+1': 3,
              os: 'Mac-@name',
              host: 'WORKSAPCE-@name',
              pid: '@natural(4, 20)',
              'ipv4|1-3': ['@ip'],
            },
          ],
        },
      }
    ));
  },
  getServer(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          getServerResponseTimeTrend: {
            'trendList|60': ['@natural(0, 1000)'],
          },
          getServerThroughputTrend: {
            'trendList|60': ['@natural(0, 10000)'],
          },
          getCPUTrend: {
            'cost|60': ['@natural(0, 99)'],
          },
          getMemoryTrend: {
            'heap|61': ['@natural(177184375, 277184375)'],
            'maxHeap|61': [377184375],
            'noheap|61': ['@natural(58260667, 68260667)'],
            'maxNoheap|61': [68260667],
          },
          getGCTrend: {
            'youngGCTime|60': ['@natural(200, 300)'],
            'oldGCTime|60': ['@natural(10,100)'],
            'youngGCCount|60': ['@natural(200, 300)'],
            'oldGCount|60': ['@natural(10,100)'],
          },
        },
      }
    ));
  },
};
