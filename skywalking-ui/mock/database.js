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

  export const getAllDatabases = () => {
    const data = mockjs.mock({
      'databaseId|20-50': [{ 'id|+1': 3, name: function() { return `database-${this.id}`; }, type: function() { return `type-${this.id}`; } }], // eslint-disable-line
    });
    return data.databaseId;
  };

  export const getTopNRecords = () => {
    const data = mockjs.mock({
      'getTopNRecords|20-50': [
        { 'traceId|+1': '@natural(200, 300).@natural(200, 300).@natural(200, 300).@natural(200, 300)', statement: function() { return `select * from database where complex = @natural(200, 300)`; }, latency: '@natural(200, 300)' }], // eslint-disable-line
    });
    return data.getTopNRecords;
  };
