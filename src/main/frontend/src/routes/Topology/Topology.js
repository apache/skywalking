import React, { Component } from 'react';
import { connect } from 'dva';
import { ChartCard } from '../../components/Charts';
import { AppTopology } from '../../components/Topology';

@connect(state => ({
  topology: state.topology,
  duration: state.global.duration,
}))
export default class Topology extends Component {
  componentDidMount() {
    this.props.dispatch({
      type: 'topology/fetch',
      payload: {},
    });
  }
  shouldComponentUpdate(nextProps) {
    if (this.props.duration !== nextProps.duration) {
      this.props.dispatch({
        type: 'topology/fetch',
        payload: {},
      });
    }
    return this.props.topology !== nextProps.topology;
  }
  render() {
    return (
      <ChartCard
        title="Topolgy Graph"
      >
        <AppTopology height={800} elements={this.props.topology.getClusterTopology} />
      </ChartCard>
    );
  }
}
