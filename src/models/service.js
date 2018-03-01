import { generateModal } from '../utils/models';

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
        ... on ServiceNode {
          sla
          calls
          numOfServiceAlarm
        }
      }
      calls {
        source
        target
        isAlert
        callType
        callsPerSec
        avgResponseTime
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
