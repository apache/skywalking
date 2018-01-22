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
    this.cy.json({ elements: this.transform(nextProps.elements), style: this.getStyle() });
    const layout = this.cy.layout({
      name: 'cose-bilkent',
      animate: false,
      idealEdgeLength: 200,
      edgeElasticity: 0.1,
    });
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
    const { height } = this.props;
    return (<div style={{ ...this.state, height }} ref={(el) => { conf.container = el; }} />);
  }
}
