import { generateBaseModal } from '../utils/utils';

export default generateBaseModal({
  namespace: 'topology',
  state: {
    getClusterTopology: {
      nodes: [],
      calls: [],
    },
  },
  query: `
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
  `,
});
