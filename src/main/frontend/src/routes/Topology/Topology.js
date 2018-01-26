import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { ChartCard } from '../../components/Charts';
import { AppTopology } from '../../components/Topology';
import { Panel } from '../../components/Page';

@connect(state => ({
  topology: state.topology,
  duration: state.global.duration,
}))
export default class Topology extends PureComponent {
  static defaultProps = {
    graphHeight: 600,
  };
  handleChange = (duration) => {
    this.props.dispatch({
      type: 'topology/fetch',
      payload: { duration },
    });
  }
  render() {
    return (
      <Panel duration={this.props.duration} onDurationChange={this.handleChange}>
        <ChartCard
          title="Topolgy Graph"
        >
          <AppTopology
            height={this.props.graphHeight}
            elements={this.props.topology.getClusterTopology}
          />
        </ChartCard>
      </Panel>
    );
  }
}
