/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import mockjs from 'mockjs';

export default {
  getServiceTopology: () => {
    const upNodes = mockjs.mock({
      'nodes|1-5': [
        {
          'id|+1': 100,
          name: '@url',
          'type|1': ['DUBBO', 'USER', 'SPRINGMVC'],
          isReal: true,
        },
      ],
    });
    const centerNodes = mockjs.mock({
      nodes: [
        {
          'id|+1': 10,
          name: '@url',
          'type|1': ['DUBBO', 'tomcat', 'SPRINGMVC'],
          isReal: true,
        },
      ],
    });
    const downNodes = mockjs.mock({
      'nodes|2-5': [
        {
          'id|+1': 200,
          name: '@url',
          'type|1': ['Oracle', 'MYSQL', 'REDIS'],
          isReal: false,
        },
      ],
    });
    downNodes.nodes.push({ id: -111 });
    const nodes = upNodes.nodes.concat(centerNodes.nodes, downNodes.nodes);
    const calls = upNodes.nodes.map(node => (mockjs.mock({
      source: node.id,
      target: 10,
      'callType|1': ['rpc', 'http', 'dubbo'],
      'cpm|0-1000': 1,
    }))).concat(downNodes.nodes.map(node => (mockjs.mock({
      source: 10,
      target: node.id,
      'callType|1': ['rpc', 'http', 'dubbo'],
      'cpm|0-2000': 1,
    }))));
    calls.push({ source: '-175', target: 10, callType: 'GRPC', cpm: 0 });
    return {
      nodes,
      calls,
    };
  },
  getEndpointTopology: () => {
    const upNodes = mockjs.mock({
      'nodes|1-5': [
        {
          'id|+1': 100,
          name: '@url',
          'type|1': ['DUBBO', 'USER', 'SPRINGMVC'],
          isReal: true,
        },
      ],
    });
    const centerNodes = mockjs.mock({
      nodes: [
        {
          'id|+1': 10,
          name: '@url',
          'type|1': ['DUBBO', 'tomcat', 'SPRINGMVC'],
          isReal: true,
        },
      ],
    });
    const downNodes = mockjs.mock({
      'nodes|2-5': [
        {
          'id|+1': 200,
          name: '@url',
          'type|1': ['Oracle', 'MYSQL', 'REDIS'],
          isReal: false,
        },
      ],
    });
    downNodes.nodes.push({ id: -111 });
    const nodes = upNodes.nodes.concat(centerNodes.nodes, downNodes.nodes);
    const calls = upNodes.nodes.map(node => (mockjs.mock({
      source: node.id,
      target: 10,
      'callType|1': ['rpc', 'http', 'dubbo'],
      'cpm|0-1000': 1,
    }))).concat(downNodes.nodes.map(node => (mockjs.mock({
      source: 10,
      target: node.id,
      'callType|1': ['rpc', 'http', 'dubbo'],
      'cpm|0-2000': 1,
    }))));
    calls.push({ source: '-175', target: 10, callType: 'GRPC', cpm: 0 });
    return {
      nodes,
      calls,
    };
  },
  getGlobalTopology: () => {
    const application = mockjs.mock({
      'nodes|2-3': [
        {
          'id|+1': 2,
          name: '@name',
          'type|1': ['DUBBO', 'tomcat', 'SPRINGMVC', 'foo'],
          isReal: true,
        },
      ],
    });
    const users = mockjs.mock({
      nodes: [
        {
          id: 1,
          name: 'User',
          type: 'USER',
          isReal: false,
        },
      ],
    });
    const resources = mockjs.mock({
      'nodes|5': [
        {
          'id|+1': 200,
          name: '@name',
          'type|1': ['Oracle', 'MYSQL', 'REDIS'],
          isReal: false,
        },
      ],
    });
    const nodes = users.nodes.concat(application.nodes, resources.nodes);
    const userConnectApplication = mockjs.mock({
      calls: [
        {
          id: 11,
          source: 1,
          target: 2,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
        {
          id: 12,
          source: 2,
          target: 3,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
        {
          id: 13,
          source: 3,
          target: 2,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
        {
          id: 14,
          source: 2,
          target: 200,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
        {
          id: 15,
          source: 2,
          target: 201,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
        {
          id: 16,
          source: 3,
          target: 202,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
        {
          id: 17,
          source: 3,
          target: 203,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
        {
          id: 18,
          source: 3,
          target: 204,
          'callType|1': ['rpc', 'http', 'dubbo'],
        },
      ],
    });
    return {
      nodes,
      calls: userConnectApplication.calls,
    };
  },
};
