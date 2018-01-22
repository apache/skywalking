import mockjs from 'mockjs';

export default {
  getDashboard(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          getClusterBrief: {
            'numOfApplication|1-100': 1,
            'numOfService|1-100': 1,
            'numOfDatabase|1-100': 1,
            'numOfCache|1-100': 1,
            'numOfMQ|1-100': 1,
          },
          getAlarmTrend: {
            'numOfAlarmRate|60': ['@natural(0, 99)'],
          },
          getConjecturalApps: {
            'apps|3-5': [{ 'name|1': ['Oracle', 'MySQL', 'ActiveMQ', 'Redis', 'Memcache', 'SQLServer'], num: '@natural(1, 20)' }],
          },
          'getTopNSlowService|10': [{ 'key|+1': 1, name: '@url', 'avgResponseTime|200-1000': 1 }],
          'getTopNServerThroughput|10': [{ 'key|+1': 1, name: '@name', 'tps|100-10000': 1 }],
        },
      }
    ));
  },
};
