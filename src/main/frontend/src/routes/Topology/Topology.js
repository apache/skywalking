import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { ChartCard } from '../../components/Charts';
import { AppTopology } from '../../components/Topology';
import { Panel } from '../../components/Page';

@connect(state => ({
  topology: state.topology,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
export default class Topology extends PureComponent {
  static defaultProps = {
    graphHeight: 600,
  };
  handleChange = (variables) => {
    this.props.dispatch({
      type: 'topology/fetchData',
      payload: { variables },
    });
  }
  render() {
    const { data } = this.props.topology;
    return (
      <Panel globalVariables={this.props.globalVariables} onChange={this.handleChange}>
        <ChartCard
          title="Topolgy Map"
        >
          <AppTopology
            height={this.props.graphHeight}
            elements={data.getClusterTopology}
          />
        </ChartCard>
      </Panel>
    );
  }
}
