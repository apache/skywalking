import React, { Component } from 'react';

export default class Panel extends Component {
  componentDidMount() {
    const { globalVariables, variables, onChange } = this.props;
    if (!this.isRender(this.props)) {
      return;
    }
    onChange({ ...globalVariables, ...variables });
  }
  shouldComponentUpdate(nextProps) {
    const { globalVariables, variables, onChange } = nextProps;
    if (!this.isRender(nextProps)) {
      return false;
    }
    if (globalVariables !== this.props.globalVariables || variables !== this.props.variables) {
      onChange({ ...globalVariables, ...variables });
      return false;
    }
    return true;
  }
  isRender = props => [props.variables, props.globalVariables]
    .reduce((acc, curr) =>
      (acc && (curr === undefined
        || (curr !== undefined && Object.keys(curr).length > 0))), true);
  render() {
    const { children } = this.props;
    return children && (<div> {children} </div>);
  }
}
