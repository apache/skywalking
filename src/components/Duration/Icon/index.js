/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import React, { PureComponent } from 'react';
import { Icon } from 'antd';
import moment from 'moment';
import lodash from 'lodash';

export default class DurationIcon extends PureComponent {
  state = {
    // noLoading: -1, loading: 1, loadingFinish: 0
    innerLoading: -1,
  }
  handleToggle = () => {
    const { loading, onToggle } = this.props;
    if (loading) {
      return;
    }
    onToggle();
  }
  renderLoad() {
    const { loading, className, onReload } = this.props;
    if (!loading && this.state.innerLoading < 1) {
      this.state.innerLoading = -1;
      return <span className={className} onClick={onReload}> <Icon type="reload" /> </span>;
    }
    if (this.state.innerLoading < 0) {
      this.state.innerLoading = 1;
      lodash.delay(() => this.setState({ innerLoading: 0 }), 1000);
    }
    return <span className={className}> <Icon type="loading" /> </span>;
  }
  render() {
    const { className, selectedDuration = {
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
          onClick={this.handleToggle}
        >
          {selectedDuration.label ? selectedDuration.label : `${selectedDuration.from().format(timeFormat)} ~ ${selectedDuration.to().format(timeFormat)}`}
          {selectedDuration.step > 0 ? ` Reloading every ${selectedDuration.step / 1000} seconds` : null }
        </span>
        {this.renderLoad()}
      </span>
    );
  }
}
