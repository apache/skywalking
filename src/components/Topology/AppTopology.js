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
    const eleWithNewUsers = this.supplyUserNode(elements.edges);
    return {
      edges: eleWithNewUsers.edges,
      nodes: nodes.filter(_ => !_.data || _.data.id !== '1').map((_) => {
        return {
          ..._,
          data: {
            ..._.data,
            size: (_.data && _.data.cpm && scale > 0) ? (scale * (_.data.cpm - minCPM)) + min : min,
          },
        };
      }).concat(eleWithNewUsers.nodes),
    };
  }
  supplyUserNode = (edges) => {
    let i = 0;
    const nodes = [];
    return {
      nodes,
      edges: edges.map((_) => {
        if (_.data.source !== '1') {
          return _;
        }
        i += 1;
        const newId = `USER-${i}`;
        nodes.push({
          data: {
            id: newId,
            name: 'User',
            type: 'USER',
          },
        });
        return {
          data: {
            ..._.data,
            source: newId,
          },
        };
      }),
    };
  }
  bindEvent = (cy) => {
    const { onSelectedApplication } = this.props;
    if (onSelectedApplication) {
      cy.on('select', 'node[sla]', (evt) => {
        const node = evt.target;
        onSelectedApplication(node.data());
      });
      cy.on('unselect', 'node[sla]', () => {
        onSelectedApplication();
      });
    }
  }
  getStyle = () => {
    return cytoscape.stylesheet()
      .selector('node[sla]')
      .css({
        width: 'data(size)',
        height: 'data(size)',
        'text-valign': 'bottom',
        'text-halign': 'center',
        'font-family': 'Microsoft YaHei',
        content: 'data(name)',
        'text-margin-y': 10,
        'border-width': ele => (ele.data('isAlarm') ? 10 : 0),
        'border-color': '#A8071A',
        'pie-1-background-color': '#2FC25B',
        'pie-1-background-size': 'data(sla)',
        'pie-1-background-opacity': 0.8,
        'pie-2-background-color': '#F04864',
        'pie-2-background-size': '100 - data(sla)',
        'pie-2-background-opacity': 0.8,
      })
      .selector(':selected')
      .css({
        'pie-size': '80%',
        'background-color': ele => (ele.data('isAlarm') ? '#A8071A' : '#1890FF'),
        'pie-1-background-opacity': 1,
        'pie-2-background-opacity': 1,
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
        label: ele => `${ele.data('callType')} \n ${ele.data('cpm')} cpm / ${ele.data('avgResponseTime')} ms`,
        'text-wrap': 'wrap',
        color: 'rgb(110, 112, 116)',
        'text-rotation': 'autorotate',
      });
  }
}
