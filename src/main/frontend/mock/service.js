import mockjs from 'mockjs';

export default {
  getService(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'searchService|5': [{}],
          getServiceResponseTimeTrend: {
            'trendList|15': ['@natural(100, 1000)'],
          },
          getServiceTPSTrend: {
            'trendList|15': ['@natural(500, 10000)'],
          },
          getServiceSLATrend: {
            'trendList|15': ['@natural(80, 100)'],
          },
        },
      }
    ));
  },
};
