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
  getAllApplication(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'applicationId|20-50': [{ 'key|+1': 3, label: function() { return `app-${this.key}`; } }], // eslint-disable-line
        },
      }
    ));
  },
  getTrace(req, res) {
    let offset = 0;
    res.json(mockjs.mock(
      {
        data: {
          queryBasicTraces: {
            'traces|20': [{
              key: '@id',
              operationName: '@url(200)',
              duration: '@natural(100, 5000)',
              start: function() { // eslint-disable-line
                offset = offset + 3600000; // eslint-disable-line
                const now = new Date().getTime(); // eslint-disable-line
                return `${now + offset}`;
              },// eslint-disable-line
              'isError|1': true,
              'traceIds|1-3': ['@guid'],
            }],
            total: '@natural(20, 1000)',
          },
        },
      }
    ));
  },
  getSpans(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          queryTrace: {
            spans: [
              {
                spanId: 1,
                segmentId: 1,
                startTime: 1516151345000,
                applicationCode: 'xx',
                endTime: 1516151355000,
                operationName: '/user/tt',
                'type|1': ['Local', 'Entry', 'Exit'],
                'component|1': ['MySQL', 'H2', 'Spring'],
                peer: '@ip',
                'tags|1-5': [{ key: 'db.type', value: 'aa' }],
                'logs|2-10': [{ 'time|+1': 1516151345000, 'data|3-8': [{ key: 'db.type', value: 'aa' }] }],
                'isError|1': true,
              },
              {
                spanId: 2,
                parentSpanId: 1,
                segmentId: 1,
                applicationCode: 'yy',
                startTime: 1516151348000,
                endTime: 1516151351000,
                operationName: '/sql/qq',
                'type|1': ['Local', 'Entry', 'Exit'],
                'component|1': ['MySQL', 'H2', 'Spring'],
                peer: '@ip',
                'tags|1-5': [{ key: 'db.type', value: 'aa' }],
                'isError|1': true,
              },
              {
                spanId: 3,
                parentSpanId: 2,
                segmentId: 1,
                applicationCode: 'yy',
                startTime: 1516151349312,
                endTime: 1516151350728,
                operationName: '/sql/qq/xxxxxxxfdfdfdfdf().xxxxx/jjjjjj',
                'type|1': ['Local', 'Entry', 'Exit'],
                'component|1': ['MySQL', 'H2', 'Spring'],
                peer: '@ip',
                'tags|1-5': [{ key: 'db.type', value: 'aa' }],
                'isError|1': true,
              },
              {
                spanId: 4,
                parentSpanId: 1,
                segmentId: 1,
                applicationCode: 'zz',
                startTime: 1516151351000,
                endTime: 1516151354000,
                operationName: '/sql/qq',
                'type|1': ['Local', 'Entry', 'Exit'],
                'component|1': ['MySQL', 'H2', 'Spring'],
                peer: '@ip',
                'tags|1-5': [{ key: 'db.type', value: 'aa' }],
                'isError|1': true,
              },
              {
                spanId: 5,
                parentSpanId: 1,
                segmentId: 1,
                applicationCode: 'zz',
                startTime: 1516151351000,
                endTime: 1516151354000,
                operationName: '/mq/producer',
                'type|1': ['Exit'],
                'component|1': ['RockerMQ'],
                peer: '@ip',
                'tags|1-5': [{ key: 'producer', value: 'tt' }],
                'isError|1': true,
              },
              {
                spanId: 6,
                segmentId: 1,
                applicationCode: 'kk',
                startTime: 1516151355000,
                endTime: 1516151360000,
                operationName: '/mq/consumer',
                'type|1': ['Entry'],
                'component|1': ['RockerMQ'],
                peer: '@ip',
                'tags|1-5': [{ key: 'consumer', value: 'tt' }],
                refs: [
                  {
                    parentSpanId: 5,
                    parentSegmentId: 1,
                  },
                ],
                'isError|1': true,
              },
              {
                spanId: 6,
                segmentId: 1,
                applicationCode: 'kk',
                startTime: 1516151355000,
                endTime: 1516151360000,
                operationName: '/mq/consumer',
                'type|1': ['Entry'],
                'component|1': ['Kafka'],
                peer: '@ip',
                'tags|1-5': [{ key: 'consumer', value: 'tt' }],
                refs: [
                  {
                    traceId: 121212,
                    type: 'CROSS_PROCESS',
                  },
                  {
                    traceId: 22223333,
                    type: 'CROSS_THREAD',
                  },
                ],
                'isError|1': true,
              },
            ],
          },
        },
      }
    ));
  },
};
