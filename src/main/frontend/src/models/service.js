import { generateModal } from '../utils/utils';

const dataQuery = `
  query Service($service: ID!, $duration: Duration!) {
    getServiceResponseTimeTrend(serverId: $serverId, duration: $duration) {
      trendList
    }
    getServiceTPSTrend(serverId: $serverId, duration: $duration) {
      trendList
    }
    getServiceSLATrend(serverId: $serverId, duration: $duration) {
      trendList
    }
    getServiceTopology(serverId: $serverId, duration: $duration) {
      nodes {
        id
        name
        type
        ... on ApplicationNode {
          sla
          callsPerSec
          responseTimePerSec
          apdex
          isAlarm
          numOfServer
          numOfServerAlarm
          numOfServiceAlarm
        }
      },
      calls: {
        source
        target
        isAlert
        callType
        callsPerSec
        responseTimePerSec
      },
    }
  }
`;

export default generateModal({
  namespace: 'service',
  state: {
    getServiceResponseTimeTrend: {
      trendList: [],
    },
    getServiceTPSTrend: {
      trendList: [],
    },
    getServiceSLATrend: {
      trendList: [],
    },
    getServiceTopology: {
      nodes: [],
      calls: [],
    },
  },
  dataQuery,
});
