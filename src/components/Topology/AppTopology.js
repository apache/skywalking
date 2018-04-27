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


import cytoscape from 'cytoscape';
import styles from './index.less';
import Base from './Base';

const conf = {
  nodeSize: {
    min: 60,
    max: 120,
  },
};
export default class AppTopology extends Base {
  setUp = (elements) => {
    const { nodes } = elements;
    const cpmArray = nodes.filter(_ => _.data && _.data.cpm).map(_ => _.data.cpm);
    const minCPM = Math.min(...cpmArray);
    const maxCPM = Math.max(...cpmArray);
    const { nodeSize: { min, max } } = conf;
    const scale = maxCPM > minCPM ? (max - min) / (maxCPM - minCPM) : 0;
    return {
      ...elements,
      nodes: nodes.map((_) => {
        return {
          ..._,
          data: {
            ..._.data,
            size: (_.data && _.data.cpm && scale > 0) ? (scale * (_.data.cpm - minCPM)) + min : min,
          },
        };
      }),
    };
  }
  bindEvent = (cy) => {
    const { onSelectedApplication } = this.props;
    cy.on('select', 'node[sla]', (evt) => {
      const node = evt.target;
      onSelectedApplication(node.data());
    });
    cy.on('unselect', 'node[sla]', () => {
      onSelectedApplication();
    });
  }
  getStyle = () => {
    return cytoscape.stylesheet()
      .selector('node[sla]')
      .css({
        width: 'data(size)',
        height: 'data(size)',
        'text-valign': 'bottom',
        'text-halign': 'center',
        'background-color': ele => (ele.data('isAlarm') ? '#A8071A' : '#1890FF'),
        'font-family': 'Microsoft YaHei',
        content: 'data(name)',
        'text-margin-y': 10,
        'pie-size': ele => (ele.data('isAlarm') ? '90%' : '100%'),
        'pie-1-background-color': '#2FC25B',
        'pie-1-background-size': 'data(sla)',
        'pie-2-background-color': '#F04864',
        'pie-2-background-size': '100 - data(sla)',
      })
      .selector(':selected')
      .css({
        'pie-size': '80%',
      })
      .selector('.faded')
      .css({
        opacity: 0.25,
        'text-opacity': 0,
      })
      .selector('node[!sla]')
      .css({
        width: 60,
        height: 60,
        'text-valign': 'bottom',
        'text-halign': 'center',
        'background-color': '#fff',
        'background-image': ele => `img/node/${ele.data('type') ? ele.data('type').toUpperCase() : 'UNDEFINED'}.png`,
        'background-width': '60%',
        'background-height': '60%',
        'border-width': 0,
        'font-family': 'Microsoft YaHei',
        label: 'data(name)',
        // 'text-margin-y': 5,
      })
      .selector('edge')
      .css({
        'curve-style': 'bezier',
        'control-point-step-size': 100,
        'target-arrow-shape': 'triangle',
        'arrow-scale': 1.7,
        'target-arrow-color': ele => (ele.data('isAlert') ? 'rgb(204, 0, 51)' : 'rgb(147, 198, 174)'),
        'line-color': ele => (ele.data('isAlert') ? 'rgb(204, 0, 51)' : 'rgb(147, 198, 174)'),
        width: 3,
        label: ele => `${ele.data('callType')} \n ${ele.data('callsPerSec')} tps / ${ele.data('avgResponseTime')} ms`,
        'text-wrap': 'wrap',
        color: 'rgb(110, 112, 116)',
        'text-rotation': 'autorotate',
      });
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
            <div>
              <img src="img/icon/data.png" class="${styles.logo}"/>${data.numOfServer}
              <img src="img/icon/alert.png" class="${styles.logo}"/>
              <span class="${styles.alert}">${data.numOfServerAlarm}</span>
            </div>
            <div>${data.callsPerSec} calls/s</div>
          </div>`;
        },
      },
    ];
  }
}
