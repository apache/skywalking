import mockjs from 'mockjs';

export default {
  getApplication(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'getSlowService|10': [{ 'key|+1': 1, name: '@name', 'avgResponseTime|200-1000': 1 }],
          'getServerThroughput|10': [{ 'key|+1': 1, name: '@name', 'tps|100-10000': 1 }],
          getApplicationTopology: () => {
            const application = mockjs.mock({
              nodes: [
                {
                  id: 1,
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
            const resources = mockjs.mock({
              'nodes|5': [
                {
                  'id|+1': 200,
                  name: '@name',
                  'type|1': ['Oracle', 'MYSQL', 'REDIS'],
                },
              ],
            });
            const nodes = application.nodes.concat(resources.nodes);
            const userConnectApplication = mockjs.mock({
              calls: [
                {
                  source: 1,
                  target: 200,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
                {
                  source: 1,
                  target: 201,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
                {
                  source: 1,
                  target: 202,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
                {
                  source: 1,
                  target: 203,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
                {
                  source: 1,
                  target: 204,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
              ],
            });
            return {
              nodes,
              calls: userConnectApplication.calls,
            };
          },
        },
      }
    ));
  },
};
