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
import * as d3 from 'd3';
import Base from './Base';

const latencyColorRange = ['#40a9ff', '#d4b106', '#cf1322'];

export default class AppTopology extends Base {
  setUp = (elements) => {
    const { nodes } = elements;
    const eleWithNewUsers = this.supplyUserNode(elements.edges);
    return {
      edges: eleWithNewUsers.edges,
      nodes: nodes.filter(_ => !_.data || _.data.id !== '1').concat(eleWithNewUsers.nodes),
    };
  }

  supplyUserNode = (edges) => {
    const nodes = [];
    return {
      nodes,
      edges: edges.map((_) => {
        if (_.data.source !== '1') {
          return {
            data: {
              ..._.data,
              dataId: _.data.id,
            },
          };
        }
        const newId = `USER-${_.data.target}`;
        nodes.push({
          data: {
            id: newId,
            name: 'User',
            type: 'USER',
            isReal: false,
          },
        });
        return {
          data: {
            ..._.data,
            source: newId,
            dataId: _.data.id,
            id: `${newId}-${_.data.target}`,
          },
        };
      }),
    };
  }

  bindEvent = (cy) => {
    const { onSelectedApplication } = this.props;
    if (onSelectedApplication) {
      cy.on('select', 'node[?isReal]', (evt) => {
        const node = evt.target;
        onSelectedApplication(node.data());
      });
      cy.on('unselect', 'node[?isReal]', () => {
        onSelectedApplication();
      });
    }
    
  }

  updateMetrics = (cy, data) => {
    this.updateNodeMetrics(cy, data);
    this.updateEdgeMetrics(cy, data);
  }

  updateNodeMetrics = (cy, data) => {
    const { sla: { values: slaValues } } = data;
    const layer = cy.cyCanvas();
    const canvas = layer.getCanvas();
    
    cy.on('render cyCanvas.resize', () => {
      const ctx = canvas.getContext('2d');
      layer.resetTransform(ctx);
      layer.clear(ctx);

      layer.setTransform(ctx);

      // Draw model elements
      cy.nodes('node[?isReal]').forEach( (node) => {
        const pos = node.position();
        layer.setTransform(ctx);
        const colors = ["#cf1322", "#40a9ff"];
        const nodeId = node.id();
        const nodeSla = slaValues.find(_ => _.id === nodeId);
        let sla = 10000;
        if (nodeSla) {
          sla = nodeSla.value;
        }

        const arc = d3.arc()
            .outerRadius(33)
            .innerRadius(27)
            .context(ctx);

        const pie = d3.pie()
            .sort(null);

        ctx.translate(pos.x, pos.y);

        const arcs = pie([10000 - sla, sla]);

        arcs.forEach((d, i) => {
          ctx.beginPath();
          arc(d);
          ctx.fillStyle = colors[i];
          ctx.fill();
        });
      });
    });
  }

  updateEdgeMetrics = (cy, data) => {
    const { cpm, latency } = data;
    if (!cpm) {
      return;
    }
    const { latencyRange } = this.props;
    const range = [0, ...latencyRange];
    const colorRange = range.map((_, i) => {
      const begin = _;
      let end = 99999;
      if (range.length > i + 1) {
        end = range[i + 1];
      }
      return {
        range: [begin, end],
        color: latencyColorRange[i],
      }
    })
    const cpmFunc = this.mapFunc(cpm.values);
    cy.style().selector('edge')
    .css({
      width: ele => cpmFunc(ele.data('dataId'), 3, 12),
      'line-color': ele => this.lineColor(latency.values, ele.data('dataId'), colorRange),
      'target-arrow-color': ele => this.lineColor(latency.values, ele.data('dataId'), colorRange),
      'curve-style': 'bezier',
      'control-point-step-size': 100,
      'target-arrow-shape': 'triangle',
      'arrow-scale': 1.2,
      'opacity': 0.666,
      'text-wrap': 'wrap',
      'text-rotation': 'autorotate',
    })
    .update();
  }

  mapFunc = (values) => {
    if (values.length < 1) {
      return (id, rLimit) => {
        return rLimit;
      }; 
    }
    const valueData = values.map(_ => _.value);
    const max = Math.max(...valueData);
    const min = Math.min(...valueData);
    const range = max - min;
    return (id, lLimit, rLimit) => {
      if (!id) {
        return lLimit;
      }
      if (range < 1) {
        return lLimit;
      }
      const value = values.find(_ => _.id === id);
      let v = min;
      if (value) {
        v = value.value;
      }
      const r = Math.round((v - min) * (rLimit - lLimit) / range + lLimit);
      if (r < lLimit) {
        return lLimit;
      }
      return r;
    };
  }

  lineColor = (values, id, colorRange) => {
    const value = values.find(_ => _.id === id);
    if (!value) {
      return '#40a9ff';
    }
    const range = colorRange.find(_ => value.value >= _.range[0] && value.value < _.range[1]);
    return range ? range.color : '#40a9ff';
  }

  getStyle = () => {
    return cytoscape.stylesheet()
      .selector('node[?isReal]')
      .css({
        width: 60,
        height: 60,
        'text-valign': 'bottom',
        'text-halign': 'center',
        'font-family': 'Microsoft YaHei',
        content: 'data(name)',
        'text-margin-y': 10,
        'border-width': 6,
        'border-color': '#40a9ff',
        'background-image': ele => `img/node/${ele.data('type') ? ele.data('type').toUpperCase() : 'UNDEFINED'}.png`,
        'background-width': '60%',
        'background-height': '60%',
        'background-color': '#e6f7ff',
      })
      .selector('node:selected')
      .css({
        width: 67,
        height: 67,
        'border-width': 13,
      })
      .selector('.faded')
      .css({
        opacity: 0.25,
        'text-opacity': 0,
      })
      .selector('node[!isReal]')
      .css({
        width: 60,
        height: 60,
        'text-valign': 'bottom',
        'text-halign': 'center',
        'background-color': '#e6f7ff',
        'background-image': ele => `img/node/${ele.data('type') ? ele.data('type').toUpperCase() : 'UNDEFINED'}.png`,
        'background-width': '60%',
        'background-height': '60%',
        'border-width': 6,
        'border-color': '#40a9ff',
        'font-family': 'Microsoft YaHei',
        label: 'data(name)',
        'text-margin-y': 10,
        // 'text-margin-y': 5,
      })
      .selector('edge')
      .css({
        'curve-style': 'bezier',
        'control-point-step-size': 100,
        'target-arrow-shape': 'triangle',
        'arrow-scale': 1.2,
        'opacity': 0.666,
        'text-wrap': 'wrap',
        'text-rotation': 'autorotate',
      });
  }
}
