import { generateModal } from '../utils/utils';

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
  `,
});
