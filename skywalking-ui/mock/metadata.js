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
  ClusterBrief: () => mockjs.mock({
      'numOfService|1-100': 1,
      'numOfEndpoint|1-100': 1,
      'numOfDatabase|1-100': 1,
      'numOfCache|1-100': 1,
      'numOfMQ|1-100': 1,
  }),
  getServiceInstances: () => {
    const data = mockjs.mock({
      'id|1-10': [{
        'id|+1': 3,
        name: '@natural(4, 20)',
        attributes: [
          {
            name: 'os_name',
            value: 'LINUX',
          },
          {
            name: 'host_name',
            value: 'WORKSAPCE-@name',
          },
          {
            name: 'process_no',
            value: '@natural(4, 20)',
          },
          {
            name: 'ipv4s',
            value: '@ip',
          },
          {
            name: 'ipv4s',
            value: '@ip',
          },
        ],
        'language|1': ['JAVA', 'RUBY', 'DOTNET'],
    }], // eslint-disable-line
    });
    return data.id;
  },
  getAllServices: () => {
    const data = mockjs.mock({
      'serviceId|20-50': [{ 'id|+1': 3, name: function() { return `service-${this.id}`; } }], // eslint-disable-line
    });
    return data.serviceId;
  },
  searchEndpoint: () => {
    const data = mockjs.mock({
      'endpointId|20-50': [{ 'id|+1': 3, name: function() { return `endpoint-${this.id}`; } }], // eslint-disable-line
    });
    return data.endpointId;
  },
  EndpointInfo: () => mockjs.mock({ id: 5, name: 'endpoint-5', serviceId: 7, serviceName: 'service-7' }),
};
