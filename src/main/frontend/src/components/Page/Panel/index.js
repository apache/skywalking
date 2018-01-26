import React, { Component } from 'react';

export default class Panel extends Component {
  componentDidMount() {
    const { duration, onDurationChange } = this.props;
    onDurationChange(duration);
  }
  shouldComponentUpdate(nextProps) {
    const { duration, onDurationChange } = this.props;
    if (duration !== nextProps.duration) {
      onDurationChange(duration);
      return false;
    }
    return true;
  }
  render() {
    const { children } = this.props;
    return children && (<div> {children} </div>);
  }
}
