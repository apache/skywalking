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


import styles from './index.less';
import Base from './Base';

export default class AppTopology extends Base {
  getStyle = () => {
    return [
      {
        selector: 'node[sla]',
        style: {
          width: 120,
          height: 120,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'background-color': '#fff',
          'border-width': 10,
          'border-color': ele => (ele.data('isAlarm') ? 'rgb(204, 0, 51)' : 'rgb(99, 160, 167)'),
          'font-family': 'Microsoft YaHei',
          label: 'data(name)',
          'text-margin-y': 10,
        },
      },
      {
        selector: 'node[!sla]',
        style: {
          width: 60,
          height: 60,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'background-color': '#fff',
          'background-image': ele => `img/node/${ele.data('type') ? ele.data('type').toUpperCase() : 'UNDEFINED'}.png`,
          'background-width': '60%',
          'background-height': '60%',
          'border-width': 3,
          'font-family': 'Microsoft YaHei',
          label: 'data(name)',
          'text-margin-y': 5,
        },
      },
      {
        selector: 'edge',
        style: {
          'curve-style': 'bezier',
          'control-point-step-size': 100,
          'target-arrow-shape': 'triangle',
          'target-arrow-color': ele => (ele.data('isAlert') ? 'rgb(204, 0, 51)' : 'rgb(147, 198, 174)'),
          'line-color': ele => (ele.data('isAlert') ? 'rgb(204, 0, 51)' : 'rgb(147, 198, 174)'),
          width: 2,
          label: ele => `${ele.data('callType')} \n ${ele.data('callsPerSec')} tps / ${ele.data('avgResponseTime')} ms`,
          'text-wrap': 'wrap',
          color: 'rgb(110, 112, 116)',
          'text-rotation': 'autorotate',
        },
      },
    ];
  }
  getNodeLabel = () => {
    return [
      {
        query: 'node[sla]',
        halign: 'center',
        valign: 'center',
        halignBox: 'center',
        valignBox: 'center',
        cssClass: `${styles.node}`,
        tpl(data) {
          return `
          <div class="${styles.circle}">
            <div class="node-percentage">${data.sla}%</div>
            <div>${data.callsPerSec} calls/s</div>
            <div>
              <img src="img/icon/data.png" class="${styles.logo}"/>${data.numOfServer}
              <img src="img/icon/alert.png" class="${styles.logo}"/>
              <span class="${styles.alert}">${data.numOfServerAlarm}</span>
            </div>
            <div>${data.apdex} Apdex</div>
          </div>`;
        },
      },
    ];
  }
}
