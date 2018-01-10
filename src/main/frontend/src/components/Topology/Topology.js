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

class Topology extends Component {
  componentDidMount() {
    this.cy = cytoscape(conf);
    this.cy.json({ elements: this.props.elements });
  }
  componentWillReceiveProps(nextProps) {
    this.cy.json(nextProps);
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
    return <div style={cyStyle} ref={(rel) => { conf.container = rel; }} />;
  }
}

export default Topology;
