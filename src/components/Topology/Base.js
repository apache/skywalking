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

cytoscape.use(coseBilkent);
cytoscape.use(dagre);

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
    const { elements, layout = config.layout } = this.props;
    this.layout = layout;
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
    if (nextProps.elements === this.elements && nextProps.layout === this.layout) {
      return;
    }
    const { elements, layout: nextLayout } = nextProps;
    const nodes = this.cy.nodes();
    let nextElements = this.transform(elements);
    if (this.setUp) {
      nextElements = this.setUp(nextElements);
    }
    this.cy.json({ elements: nextElements, style: this.getStyle() });
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
  transform(elements) {
    if (!elements) {
      return [];
    }
    this.elements = elements;
    const { nodes, calls } = elements;
    return {
      nodes: nodes.map(node => ({ data: node })),
      edges: calls.filter(call => (nodes.findIndex(node => node.id === call.source) > -1
        && nodes.findIndex(node => node.id === call.target) > -1))
        .map(call => ({ data: { ...call, id: `${call.source}-${call.target}` } })),
    };
  }
  render() {
    return (<div style={{ ...this.props }} ref={(el) => { this.container = el; }} />);
  }
}
