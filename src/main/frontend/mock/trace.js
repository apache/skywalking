import mockjs from 'mockjs';

export default {
  getTrace(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          queryBasicTraces: {
            'traces|10': [{
              operationName: '@url',
              duration: '@natural(100, 1000)',
              start: '@datetime',
              'isError|1': true,
              traceId: '@guid',
            }],
            total: '@natural(5, 50)',
          },
        },
      }
    ));
  },
};
