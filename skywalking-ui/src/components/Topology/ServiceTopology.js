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


import Base from './Base';

export default class ServiceTopology extends Base {
  getStyle = () => {
    return [
      {
        selector: 'node[calls]',
        style: {
          width: 120,
          height: 120,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'background-color': '#fff',
          'border-width': 3,
          'border-color': ele => (ele.data('numOfServiceAlarm') > 0 ? 'red' : 'rgb(99, 160, 167)'),
          'font-family': 'Microsoft YaHei',
          label: 'data(name)',
        },
      },
      {
        selector: 'node[!calls]',
        style: {
          width: 60,
          height: 60,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'background-color': '#fff',
          'background-image': ele => `img/node/${ele.data('type') ? ele.data('type') : 'UNDEFINED'}.png`,
          'background-width': '60%',
          'background-height': '60%',
          'border-width': 1,
          'font-family': 'Microsoft YaHei',
          label: 'data(name)',
        },
      },
      {
        selector: 'edge',
        style: {
          'curve-style': 'bezier',
          'control-point-step-size': 100,
          'target-arrow-shape': 'triangle',
          'target-arrow-color': ele => (ele.data('isAlarm') ? 'red' : 'rgb(147, 198, 174)'),
          'line-color': ele => (ele.data('isAlarm') ? 'red' : 'rgb(147, 198, 174)'),
          width: 2,
          label: ele => `${ele.data('callType')} \n ${ele.data('cpm')} cpm / ${ele.data('avgResponseTime')} ms`,
          'text-wrap': 'wrap',
          color: 'rgb(110, 112, 116)',
          'text-rotation': 'autorotate',
        },
      },
    ];
  }
}
