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
    res.json(mockjs.mock(
      {
        data: {
          queryBasicTraces: {
            'traces|10': [{
              key: '@url',
              duration: '@natural(100, 1000)',
              start: '@datetime',
              'isError|1': true,
              'traceIds|1-3': ['@guid'],
            }],
            total: '@natural(5, 50)',
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
              },
              {
                spanId: 3,
                parentSpanId: 2,
                segmentId: 1,
                applicationCode: 'yy',
                startTime: 1516151349312,
                endTime: 1516151350728,
                operationName: '/sql/qq',
                'type|1': ['Local', 'Entry', 'Exit'],
                'component|1': ['MySQL', 'H2', 'Spring'],
                peer: '@ip',
                'tags|1-5': [{ key: 'db.type', value: 'aa' }],
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
              },
            ],
          },
        },
      }
    ));
  },
};
