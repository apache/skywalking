import mockjs from 'mockjs';

export default {
  getApplication(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'getSlowService|10': [{ 'key|+1': 1, name: '@name', 'avgResponseTime|200-1000': 1 }],
          'getServerThroughput|10': [{ 'key|+1': 1, name: '@name', 'tps|100-10000': 1 }],
        },
      }
    ));
  },
};
