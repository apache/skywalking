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
import { Select, Spin } from 'antd';
import debounce from 'lodash.debounce';
import request from '../../../utils/request';

const { Option } = Select;

export default class Search extends PureComponent {
  constructor(props) {
    super(props);
    this.lastFetchId = 0;
    this.originFetchServer = this.fetchServer;
    this.fetchServer = debounce(this.fetchServer, 800);
  }
  state = {
    data: [],
    fetching: false,
  }
  componentDidMount() {
    this.originFetchServer('', true);
  }
  fetchServer = (value, isSelectOne) => {
    if (value === undefined) {
      return;
    }
    const { url, query, variables = {}, transform } = this.props;
    this.lastFetchId += 1;
    const fetchId = this.lastFetchId;
    this.setState({ data: [], fetching: true });
    request(`/api${url}`, {
      method: 'POST',
      body: {
        variables: {
          ...variables,
          keyword: value,
        },
        query,
      },
    })
      .then((body) => {
        if (!body.data || fetchId !== this.lastFetchId) { // for fetch callback order
          return;
        }
        const list = body.data[Object.keys(body.data)[0]];
        this.setState({ data: (transform ? list.map(transform) : list), fetching: false });
        if (isSelectOne && this.state.data.length > 0) {
          this.handleSelect(this.state.data[0]);
        }
      });
  }
  handleSelect = (value) => {
    const { onSelect } = this.props;
    const selected = this.state.data.find(_ => _.key === value.key);
    onSelect(selected);
  }
  render() {
    const { placeholder, value } = this.props;
    return (
      <Select
        showSearch
        style={{ width: 600 }}
        placeholder={placeholder}
        notFoundContent={this.state.fetching ? <Spin size="small" /> : null}
        filterOption={false}
        labelInValue
        onSelect={this.handleSelect.bind(this)}
        onSearch={this.fetchServer}
        value={value}
      >
        {this.state.data.map((_) => {
            return (<Option key={_.key} value={_.key}>{_.label}</Option>);
          })}
      </Select>
    );
  }
}
