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
import nodeHtmlLabel from 'cytoscape-node-html-label';
import conf from './conf';

cytoscape.use(coseBilkent);
cytoscape.use(nodeHtmlLabel);

export default class Base extends Component {
  state= {
    height: '600px',
    display: 'block',
  }
  componentDidMount() {
    this.cy = cytoscape({
      ...conf,
      elements: this.transform(this.props.elements),
      style: this.getStyle(),
    });
    this.cy.nodeHtmlLabel(this.getNodeLabel());
  }
  componentWillReceiveProps(nextProps) {
    if (nextProps.elements === this.elements) {
      return;
    }
    const nodes = this.cy.nodes();
    const nextElements = this.transform(nextProps.elements);
    this.cy.json({ elements: nextElements, style: this.getStyle() });
    if (this.isSame(nodes, this.cy.nodes())) {
      return;
    }
    const { layout: layoutConfig = {
      name: 'cose-bilkent',
      animate: false,
      idealEdgeLength: 200,
      edgeElasticity: 0.1,
    } } = this.props;
    const layout = this.cy.layout(layoutConfig);
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
    const { height = this.state.height } = this.props;
    return (<div style={{ ...this.state, height }} ref={(el) => { conf.container = el; }} />);
  }
}
