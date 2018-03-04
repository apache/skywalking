import mockjs from 'mockjs';

export default {
  searchService(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'searchService|5': [
            {
              'key|+1': 3,
              label: function() { return `service-${this.key}`; }, // eslint-disable-line
            },
          ],
        },
      }
    ));
  },
  getService(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'searchService|5': [{}],
          getServiceResponseTimeTrend: {
            'trendList|60': ['@natural(100, 1000)'],
          },
          getServiceTPSTrend: {
            'trendList|60': ['@natural(500, 10000)'],
          },
          getServiceSLATrend: {
            'trendList|60': ['@natural(8000, 10000)'],
          },
          getServiceTopology: () => {
            const upNodes = mockjs.mock({
              'nodes|1-5': [
                {
                  'id|+1': 100,
                  name: '@url',
                  'type|1': ['DUBBO', 'USER', 'SPRINGMVC'],
                  'calls|1000-2000': 1,
                  'sla|1-100.1-2': 1,
                  'numOfServiceAlarm|1-100': 1,
                },
              ],
            });
            const centerNodes = mockjs.mock({
              nodes: [
                {
                  'id|+1': 1,
                  name: '@url',
                  'type|1': ['DUBBO', 'tomcat', 'SPRINGMVC'],
                  'calls|1000-2000': 1,
                  'sla|1-100.1-2': 1,
                  'numOfServiceAlarm|1-100': 1,
                },
              ],
            });
            const downNodes = mockjs.mock({
              'nodes|2-5': [
                {
                  'id|+1': 200,
                  name: '@url',
                  'type|1': ['Oracle', 'MYSQL', 'REDIS'],
                },
              ],
            });
            const nodes = upNodes.nodes.concat(centerNodes.nodes, downNodes.nodes);
            const calls = upNodes.nodes.map(node => (mockjs.mock({
              source: node.id,
              target: 1,
              'isAlert|1': true,
              'callType|1': ['rpc', 'http', 'dubbo'],
              'callsPerSec|0-1': 1,
              'avgResponseTime|500-5000': 1,
            }))).concat(downNodes.nodes.map(node => (mockjs.mock({
              source: 1,
              target: node.id,
              'isAlert|1': true,
              'callType|1': ['rpc', 'http', 'dubbo'],
              'callsPerSec|0-2000': 1,
              'avgResponseTime|500-5000': 1,
            }))));
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
