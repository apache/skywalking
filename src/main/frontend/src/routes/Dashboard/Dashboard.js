import React, { PureComponent } from 'react';
import { connect } from 'dva';

@connect(state => ({
  dashboard: state.dashboard,
}))
export default class Dashboard extends PureComponent {
  render() {
    return (
      <div>test</div>
    );
  }
}
