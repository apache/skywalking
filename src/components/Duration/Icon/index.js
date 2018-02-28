import React, { PureComponent } from 'react';
import { Icon } from 'antd';
import moment from 'moment';

export default class DurationIcon extends PureComponent {
  render() {
    const { className, onToggle, onReload, selectedDuration = {
      from() {
        return moment();
      },
      to() {
        return moment();
      },
      lable: 'NaN',
    } } = this.props;
    const timeFormat = 'YYYY-MM-DD HH:mm';
    return (
      <span>
        <span
          className={className}
          onClick={onToggle}
        >
          {selectedDuration.label ? selectedDuration.label : `${selectedDuration.from().format(timeFormat)} ~ ${selectedDuration.to().format(timeFormat)}`}
          {selectedDuration.step > 0 ? ` Reloading every ${selectedDuration.step / 1000} seconds` : null }
        </span>
        <span className={className} onClick={onReload}> <Icon type="reload" /> </span>
      </span>
    );
  }
}
