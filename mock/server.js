import mockjs from 'mockjs';

export default {
  searchServer(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'searchServer|5': [
            {
              'key|+1': 3,
              name: function() { return `server-${this.key}`; }, // eslint-disable-line
              os: 'Mac-@name',
              host: 'WORKSAPCE-@name',
              pid: '@natural',
              'ipv4|1-3': ['@ip'],
            },
          ],
        },
      }
    ));
  },
  getServer(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          getServerResponseTimeTrend: {
            'trendList|60': ['@natural(100, 1000)'],
          },
          getServerTPSTrend: {
            'trendList|60': ['@natural(500, 10000)'],
          },
          getCPUTrend: {
            'cost|60': ['@natural(0, 99)'],
          },
          getMemoryTrend: {
            'heap|61': ['@natural(500, 900)'],
            'maxHeap|61': [1000],
            'noheap|61': ['@natural(100, 200)'],
            'maxNoheap|61': [300],
          },
          getGCTrend: {
            'youngGC|60': ['@natural(200, 300)'],
            'oldGC|60': ['@natural(10,100)'],
          },
        },
      }
    ));
  },
};
