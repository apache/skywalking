import React, { Component } from 'react';
import cytoscape from 'cytoscape';
import coseBilkent from 'cytoscape-cose-bilkent';
import nodeHtmlLabel from 'cytoscape-node-html-label';
import conf from './conf';

cytoscape.use(coseBilkent);
cytoscape.use(nodeHtmlLabel);

const cyStyle = {
  height: '400px',
  display: 'block',
};

export default class Base extends Component {
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
    this.cy.layout({
      name: 'cose',
      animate: true,
    }).run();
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
    this.elements = elements;
    const { nodes, calls } = elements;
    return {
      nodes: nodes.map(node => ({ data: node })),
      edges: calls.filter(call => (nodes.findIndex(node => node.id === call.source)
        && nodes.findIndex(node => node.id === call.target)))
        .map(call => ({ data: { ...call, id: `${call.source}-${call.target}` } })),
    };
  }
  render() {
    return (<div style={cyStyle} ref={(el) => { conf.container = el; }} />);
  }
}
