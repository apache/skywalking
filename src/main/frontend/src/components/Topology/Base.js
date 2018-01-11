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
    this.elements = this.props.elements;
    const { nodes, calls } = this.props.elements;
    this.cy = cytoscape({ ...conf, elements: { nodes, edges: calls }, style: this.getStyle() });
    this.cy.nodeHtmlLabel(this.getNodeLabel());
  }
  componentWillReceiveProps(nextProps) {
    if (nextProps.elements === this.elements) {
      return;
    }
    this.elements = nextProps.elements;
    const { nodes, calls } = this.elements;
    this.cy.json({ elements: { nodes, edges: calls }, style: this.getStyle() });
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
  render() {
    return (<div style={cyStyle} ref={(el) => { conf.container = el; }} />);
  }
}
