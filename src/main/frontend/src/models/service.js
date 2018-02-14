import { generateModal } from '../utils/utils';

const dataQuery = `
  query Service($serviceId: ID!, $duration: Duration!) {
    getServiceResponseTimeTrend(serviceId: $serviceId, duration: $duration) {
      trendList
    }
    getServiceTPSTrend(serviceId: $serviceId, duration: $duration) {
      trendList
    }
    getServiceSLATrend(serviceId: $serviceId, duration: $duration) {
      trendList
    }
    getServiceTopology(serviceId: $serviceId, duration: $duration) {
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
      }
      calls {
        source
        target
        isAlert
        callType
        callsPerSec
        responseTimePerSec
      }
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
