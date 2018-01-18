import mockjs from 'mockjs';

export default {
  getServer(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'searchServer|5': [{}],
          getServerResponseTimeTrend: {
            'trendList|15': ['@natural(100, 1000)'],
          },
          getServerTPSTrend: {
            'trendList|15': ['@natural(500, 10000)'],
          },
          getCPUTrend: {
            'cost|15': ['@natural(0, 99)'],
          },
          getMemoryTrend: {
            'heap|15': ['@natural(500, 900)'],
            'maxHeap|15': [1000],
            'noheap|15': ['@natural(100, 200)'],
            'maxNoheap|15': [300],
          },
          getGCTrend: {
            'youngGC|15': ['@natural(200, 300)'],
            'oldGC|15': ['@natural(10,100)'],
          },
        },
      }
    ));
  },
};
