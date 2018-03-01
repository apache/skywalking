import { generateModal } from '../utils/models';

export default generateModal({
  namespace: 'topology',
  state: {
    getClusterTopology: {
      nodes: [],
      calls: [],
    },
  },
  dataQuery: `
    query Topology($duration: Duration!) {
      getClusterTopology(duration: $duration) {
        nodes {
          id
          name
          type
          ... on ApplicationNode {
            sla
            callsPerSec
            avgResponseTime
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
          avgResponseTime
        }
      }
    }
  `,
});
