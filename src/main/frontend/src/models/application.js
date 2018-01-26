import { generateBaseModal } from '../utils/utils';
import { query as queryService } from '../services/graphql';

const allAppQuery = `
  query AllApplication($duration: Duration!) {
    getAllApplication(duration: $duration) {
      key: id
      name
    }
  }
`;

const appQuery = `
  query Application($applicationId: ID!, $duration: Duration!) {
    getSlowService(applicationId: $applicationId, duration: $duration) {
      key: id
      name
      avgResponseTime
    }
    getServerThroughput(applicationId: $applicationId, duration: $duration) {
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

export default generateBaseModal({
  namespace: 'application',
  effects: {
    *loadAllApp({ payload }, { call, put }) {
      const { data: { getAllApplication: allApplication } } = yield call(queryService, 'application', { variables: payload, query: allAppQuery });
      const applicationId = allApplication && allApplication.length > 0 && allApplication[0].key;
      if (!applicationId) {
        return;
      }
      yield put({
        type: 'saveApplication',
        payload: { allApplication, applicationId },
      });
      const response = yield put({
        type: 'fetch',
        payload: { variables: { ...payload, applicationId }, query: appQuery },
      });
      yield put({
        type: 'save',
        payload: response,
      });
    },
  },
  reducers: {
    saveApplication(preState, action) {
      const { applicationId } = preState;
      const { allApplication, applicationId: newApplicationId } = action.payload;
      if (allApplication.find(_ => _.key === applicationId)) {
        return {
          ...preState,
          allApplication,
        };
      }
      return {
        ...preState,
        allApplication,
        applicationId: newApplicationId,
      };
    },
  },
  state: {
    allApplication: [],
    getSlowService: [],
    getServerThroughput: [],
    getApplicationTopology: {
      nodes: [],
      calls: [],
    },
  },
  query: appQuery,
});
