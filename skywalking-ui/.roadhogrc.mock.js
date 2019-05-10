import fs from 'fs';
import { delay } from 'roadhog-api-doc';
import { getGlobalTopology, getServiceTopology, getEndpointTopology } from './mock/topology';
import { Alarms, AlarmTrend } from './mock/alarm';
import { TraceBrief, Trace } from './mock/trace'
import { getAllDatabases, getTopNRecords } from './mock/database'
import { makeExecutableSchema, addMockFunctionsToSchema } from 'graphql-tools';
import { graphql } from 'graphql';
import { ClusterBrief, getServiceInstances, getAllServices, searchEndpoint, EndpointInfo } from './mock/metadata';
import { IntValues, Thermodynamic } from './mock/metric';
import { getServiceTopN, getAllEndpointTopN, getServiceInstanceTopN, getEndpointTopN } from './mock/aggregation';

const noMock = process.env.NO_MOCK === 'true';

const resolvers = {
  Query: {
    getAllServices,
    getAllDatabases,
    getTopNRecords,
    getServiceInstances,
    getServiceTopN,
    getAllEndpointTopN,
    getGlobalTopology,
    getServiceTopology,
    getEndpointTopology,
    searchEndpoint,
    getEndpointTopN,
    getServiceInstanceTopN,
  }
}

const schema = makeExecutableSchema({ typeDefs: [
  "scalar Long",
  fs.readFileSync('query-protocol/common.graphqls', 'utf8'),
  fs.readFileSync('query-protocol/metadata.graphqls', 'utf8'),
  fs.readFileSync('query-protocol/top-n-records.graphqls', 'utf8'),
  fs.readFileSync('query-protocol/alarm.graphqls', 'utf8'),
  fs.readFileSync('query-protocol/metric.graphqls', 'utf8'),
  fs.readFileSync('query-protocol/aggregation.graphqls', 'utf8'),
  fs.readFileSync('query-protocol/trace.graphqls', 'utf8'),
  fs.readFileSync('query-protocol/topology.graphqls', 'utf8'),
], resolvers });

addMockFunctionsToSchema({
  schema,
  mocks: {
    Long: () => 1,
    ClusterBrief,
    Thermodynamic,
    AlarmTrend,
    Alarms,
    TraceBrief,
    Trace,
    IntValues,
    EndpointInfo,
  },
  preserveResolvers: true
});

const proxy = {
  'POST /api/graphql': (req, res) => {
    const { query: source, variables: variableValues } = req.body;
    graphql({ schema, source, variableValues }).then((result) => res.send(result));
  },
  'POST /api/login/account': (req, res) => {
    const { password, userName } = req.body;
    if (password === '888888' && userName === 'admin') {
      res.send({
        status: 'ok',
        currentAuthority: 'admin',
      });
      return;
    }
    res.send({
      status: 'error',
      currentAuthority: 'guest',
    });
  },
};

export default noMock ? {} : delay(proxy, 1000);
