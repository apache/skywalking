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


import React, { Component } from 'react';
import cytoscape from 'cytoscape';
import coseBilkent from 'cytoscape-cose-bilkent';
import dagre from 'cytoscape-dagre';
import cyCanvas from 'cytoscape-canvas';

cytoscape.use(coseBilkent);
cytoscape.use(dagre);
cytoscape.use(cyCanvas);

const config = {
  layout: {
    name: 'cose-bilkent',
    animate: true,
    idealEdgeLength: 200,
    edgeElasticity: 0.1,
  },
};
export default class Base extends Component {
  static defaultProps = {
    height: '600px',
    display: 'block',
  }

  componentDidMount() {
    const { elements, layout = config.layout, metrics } = this.props;
    this.layout = layout;
    this.metrics = metrics;
    this.elements = elements;
    let nextElements = this.transform(elements);
    if (this.setUp) {
      nextElements = this.setUp(nextElements);
    }
    this.cy = cytoscape({
      container: this.container,
      zoom: 1,
      maxZoom: 1,
      boxSelectionEnabled: true,
      wheelSensitivity: 0.2,
      layout,
      elements: nextElements,
      style: this.getStyle(),
    });
    if (this.bindEvent) {
      this.bindEvent(this.cy);
    }
  }

  componentWillReceiveProps(nextProps) {
    this.updateTopology(nextProps);
    this.updateMetric(nextProps);
  }

  shouldComponentUpdate() {
    return false;
  }

  componentWillUnmount() {
    this.cy.destroy();
  }

  getCy() {
    return this.cy;
  }

  isSame = (nodes, nextNodes) => {
    if (nodes.length !== nextNodes.length) {
      return false;
    }
    const diff = nextNodes.diff(nodes);
    return diff.left.length < 1 && diff.right.length < 1;
  }

  loadMetrics = (elementes) => {
    const { onLoadMetircs } = this.props;
    if (onLoadMetircs) {
      onLoadMetircs(
        elementes.nodes.filter(_ => _.data.id.indexOf('USER') < 0).map(_ => _.data.id),
        elementes.edges.filter(_ => _.data.detectPoint === 'SERVER').map(_ => _.data.dataId),
        elementes.edges.filter(_ => _.data.detectPoint === 'CLIENT').map(_ => _.data.dataId),
      );
    }
  }

  transform = (elements) => {
    if (!elements) {
      return [];
    }
    const { nodes, calls } = elements;
    return {
      nodes: nodes.map(node => ({ data: node })),
      edges: calls.filter(call => (nodes.findIndex(node => node.id === call.source) > -1
        && nodes.findIndex(node => node.id === call.target) > -1))
        .map(call => ({ data: { ...call } })),
    };
  }

  updateTopology(nextProps) {
    const { elements, layout: nextLayout, appRegExps } = nextProps;
    let thisElements = this.elements;
    let nextElements = elements;
    const filteredElements = this.filter(elements, appRegExps);
    if (filteredElements) {
      thisElements = this.filteredElements;
      nextElements = filteredElements;
      this.filteredElements = filteredElements;
    }
    if (thisElements === nextElements && nextLayout === this.layout) {
      return;
    }
    this.elements = elements;
    nextElements = this.transform(nextElements);
    if (this.setUp) {
      nextElements = this.setUp(nextElements);
    }
    const nodes = this.cy.nodes();
    this.cy.json({ elements: nextElements });
    
    if (this.bindEvent) {
      this.bindEvent(this.cy);
    }
    this.loadMetrics(nextElements);
    if (nextLayout === this.layout && this.isSame(nodes, this.cy.nodes())) {
      return;
    }
    this.layout = nextLayout;
    const layout = this.cy.layout(nextLayout);
    layout.pon('layoutstop').then(() => {
      this.cy.minZoom(this.cy.zoom() - 0.3);
    });
    layout.run();
  }

  updateMetric(nextProps) {
    if (nextProps.metrics === this.metrics && nextProps.latencyRange === this.latencyRange) {
      return;
    }
    this.metrics = nextProps.metrics;
    this.latencyRange = nextProps.latencyRange;
    if (this.updateMetrics) {
      this.updateMetrics(this.cy, this.metrics);
    }
  }

  filter(elements, appRegExps) {
    if (!appRegExps) {
      this.appRegExps = appRegExps;
      return elements;
    }
    if (this.elements === elements && this.appRegExps === appRegExps) {
      return this.filteredElements;
    }
    this.appRegExps = appRegExps;
    const nn = elements.nodes.filter(_ => appRegExps
      .findIndex(r => _.name.match(r)) > -1);
    const cc = elements.calls.filter(_ => nn
      .findIndex(n => n.id === _.source || n.id === _.target) > -1);
    return {
      nodes: elements.nodes.filter(_ => cc
        .findIndex(c => c.source === _.id || c.target === _.id) > -1),
      calls: cc,
    };
  }

  render() {
    return (<div style={{ ...this.props }} ref={(el) => { this.container = el; }} />);
  }
}
