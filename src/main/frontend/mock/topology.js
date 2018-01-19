import mockjs from 'mockjs';

export default {
  getTopology(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          getClusterTopology: () => {
            const application = mockjs.mock({
              'nodes|2': [
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
              nodes: [
                {
                  id: 100,
                  name: 'User',
                  type: 'USER',
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
            const nodes = users.nodes.concat(application.nodes, resources.nodes);
            const userConnectApplication = mockjs.mock({
              calls: [
                {
                  source: 100,
                  target: 1,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
                {
                  source: 1,
                  target: 2,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
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
                  source: 2,
                  target: 202,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
                {
                  source: 2,
                  target: 203,
                  'isAlarm|1': true,
                  'callType|1': ['rpc', 'http', 'dubbo'],
                  'callsPerSec|100-2000': 1,
                  'responseTimePerSec|500-5000': 1,
                },
                {
                  source: 2,
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
