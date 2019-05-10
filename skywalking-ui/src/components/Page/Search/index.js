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

import React, { Component } from 'react';
import { Select, Spin } from 'antd';
import debounce from 'lodash.debounce';
import request from '../../../utils/request';

const { Option } = Select;

export default class Search extends Component {
  constructor(props) {
    super(props);
    this.lastFetchId = 0;
    this.originFetchServer = this.fetchServer;
    this.fetchServer = debounce(this.fetchServer, 800);
    this.state = {
      data: [],
      fetching: false,
    };
  }

  componentDidMount() {
    const {...propsData} = this.props;
    if (propsData.variables && Object.keys(propsData.variables).length > 0) {
      this.originFetchServer('', propsData.value);
    }
  }

  componentDidUpdate(prevProps) {
    const {...propsData} = this.props;
    if (prevProps.variables !== propsData.variables) {
      this.originFetchServer('', propsData.value);
    }
  }

  fetchServer = (keyword, value) => {
    if (keyword === undefined) {
      return;
    }
    const { url, query, variables = {}, transform } = this.props;
    const that = this;
    that.lastFetchId += 1;
    const fetchId = that.lastFetchId;
    this.setState({ data: [], fetching: true });
    request(`/api${url}`, {
      method: 'POST',
      body: {
        variables: {
          ...variables,
          keyword,
        },
        query,
      },
    }).then(body => {
      if (!body.data || fetchId !== that.lastFetchId) {
        // for fetch callback order
        return;
      }
      const list = body.data[Object.keys(body.data)[0]];
      this.setState({ data: transform ? list.map(transform) : list, fetching: false });
      if (that.state.data.length < 1) {
        return;
      }
      if (!value) {
        return;
      }
      const { key, label } = value;
      if (!key || key.length < 1) {
        this.handleSelect(that.state.data[0]);
        return;
      }
      const option = that.state.data.find(_ => _.key === key);
      if (option) {
        return;
      }
      const target = {key, label};
      const newList = [...that.state.data, target];
      this.setState({data: newList});
      this.handleSelect(target);
    });
  };

  handleSelect = value => {
    const { onSelect } = this.props;
    const that = this;
    const selected = that.state.data.find(_ => _.key === value.key);
    onSelect(selected);
  };

  render() {
    const { placeholder, value } = this.props;
    const { ...stateData } = this.state;
    return (
      <Select
        showSearch
        style={{ width: 600 }}
        placeholder={placeholder}
        notFoundContent={stateData.fetching ? <Spin size="small" /> : null}
        filterOption={false}
        labelInValue
        onSelect={this.handleSelect.bind(this)}
        onSearch={this.fetchServer}
        value={value}
      >
        {stateData.data.map(_ => {
          return (
            <Option key={_.key} value={_.key}>
              {_.label}
            </Option>
          );
        })}
      </Select>
    );
  }
}
