import { generateModal } from '../utils/utils';

export default generateModal({
  namespace: 'dashboard',
  state: {
    getClusterBrief: {
      numOfApplication: 0,
      numOfService: 0,
      numOfDatabase: 0,
      numOfCache: 0,
      numOfMQ: 0,
    },
    getAlarmTrend: {
      numOfAlarmRate: [],
    },
    getConjecturalApps: {
      apps: [],
    },
    getTopNSlowService: [],
    getTopNServerThroughput: [],
  },
  dataQuery: `
    query Dashboard($duration: Duration!) {
      getClusterBrief(duration: $duration) {
        numOfApplication
        numOfService
        numOfDatabase
        numOfCache
        numOfMQ
      }
      getAlarmTrend(duration: $duration) {
        numOfAlarmRate
      }
      getConjecturalApps(duration: $duration) {
        apps {
          name
          num
        }
      }
      getTopNSlowService(duration: $duration, topN: 10) {
        key: id
        name
        avgResponseTime
      }
      getTopNApplicationThroughput(duration: $duration, topN: 10) {
        key: applicationId
        applicationCode
        tps
      }
    }
  `,
});
