import { generateModal } from '../utils/utils';

const dataQuery = `
  query Application($serverId: ID!, $duration: Duration!) {
    getServerResponseTimeTrend(serverId: $serverId, duration: $duration) {
      trendList
    }
    getServerTPSTrend(serverId: $serverId, duration: $duration) {
      trendList
    }
    getCPUTrend(serverId: $serverId, duration: $duration) {
      cost
    }
    getGCTrend(serverId: $serverId, duration: $duration) {
      youngGC
      oldGC
    }
    getMemoryTrend(serverId: $serverId, duration: $duration) {
      heap
      maxHeap
      noheap
      maxNoheap
    }
  }
`;

export default generateModal({
  namespace: 'server',
  state: {
    serverInfo: {},
    getServerResponseTimeTrend: {
      trendList: [],
    },
    getServerTPSTrend: {
      trendList: [],
    },
    getCPUTrend: {
      cost: [],
    },
    getMemoryTrend: {
      heap: [],
      maxHeap: [],
      noheap: [],
      maxNoheap: [],
    },
    getGCTrend: {
      youngGC: [],
      oldGC: [],
    },
  },
  dataQuery,
});
