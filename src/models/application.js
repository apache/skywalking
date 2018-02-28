import { generateModal } from '../utils/models';

const optionsQuery = `
  query ApplicationOption($duration: Duration!) {
    applicationId: getAllApplication(duration: $duration) {
      key: id
      label: name
    }
  }
`;

const dataQuery = `
  query Application($applicationId: ID!, $duration: Duration!) {
    getSlowService(applicationId: $applicationId, duration: $duration, topN: 10) {
      key: id
      name
      avgResponseTime
    }
    getServerThroughput(applicationId: $applicationId, duration: $duration, topN: 10) {
      key: id
      name
      tps
    }
    getApplicationTopology(applicationId: $applicationId, duration: $duration) {
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
  namespace: 'application',
  state: {
    allApplication: [],
    getSlowService: [],
    getServerThroughput: [],
    getApplicationTopology: {
      nodes: [],
      calls: [],
    },
  },
  optionsQuery,
  dataQuery,
});
