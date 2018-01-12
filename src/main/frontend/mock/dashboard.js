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
            'numOfAlarmRate|15': ['@natural(0, 99)'],
          },
          getConjecturalApps: {
            'apps|3-5': [{ 'name|1': ['Oracle', 'MySQL', 'ActiveMQ', 'Redis', 'Memcache', 'SQLServer'], num: '@natural(1, 20)' }],
          },
          'getTopNSlowService|10': [{ 'key|+1': 1, name: '@name', 'avgResponseTime|200-1000': 1 }],
          'getTopNServerThroughput|10': [{ 'key|+1': 1, name: '@name', 'tps|100-10000': 1 }],
          getClusterTopology: () => {
            const application = mockjs.mock({
              'nodes|5-20': [
                {
                  'id|+1': 1,
                  name: '@name',
                  'type|1': ['DUBBO', 'tomcat', 'SPRINGMVC'],
                  'calls|1000-2000': 1,
                  'sla|1-100.1-2': 1,
                  'apdex|0.2': 1,
                  'numOfServer|1-100': 1,
                  'numOfServerAlarm|1-100': 1,
                  'numOfServiceAlarm|1-100': 1,
                  'isIncomingNode|1': true,
                },
              ],
            });
            const users = mockjs.mock({
              'nodes|1-3': [
                {
                  'id|+1': 100,
                  name: 'User',
                  type: 'USER',
                },
              ],
            });
            const resources = mockjs.mock({
              'nodes|2-5': [
                {
                  'id|+1': 200,
                  name: '@name',
                  'type|1': ['Oracle', 'MYSQL', 'REDIS'],
                },
              ],
            });
            const nodes = application.nodes.concat(users.nodes, resources.nodes);
            const userConnectApplication = mockjs.mock({
              'calls|1-3': [{
                'source|+1': 100,
                'target|+1': 1,
                'isAlarm|1': true,
                'callType|1': ['rpc', 'http', 'dubbo'],
                'callsPerSec|100-2000': 1,
                'responseTimePerSec|500-5000': 1,
              }],
            });
            const applicationConnectApplication = mockjs.mock({
              'calls|20-50': [{
                'source|+1': 1,
                'target|+1': 5,
                'isAlarm|1': true,
                'callType|1': ['rpc', 'http', 'dubbo'],
                'callsPerSec|100-2000': 1,
                'responseTimePerSec|500-5000': 1,
              }],
            });
            const applicationConnectResources = mockjs.mock({
              'calls|5-10': [{
                'source|+1': 1,
                'target|+1': 200,
                'isAlarm|1': true,
                'callType|1': ['rpc', 'http', 'dubbo'],
                'callsPerSec|100-2000': 1,
                'responseTimePerSec|500-5000': 1,
              }],
            });
            const calls = userConnectApplication.calls
              .concat(applicationConnectApplication.calls, applicationConnectResources.calls);
            return {
              nodes,
              calls,
            };
          },
        },
      }
    ));
  },
};
