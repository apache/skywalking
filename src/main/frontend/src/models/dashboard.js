import { generateBaseModal } from '../utils/utils';

export default generateBaseModal({
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
  query: `
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
        id
        name
        avgResponseTime
      }
      getTopNServerThroughput(duration: $duration, topN: 10) {
        id
        name
        tps
      }
    }
  `,
});
