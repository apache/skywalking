import React, { PureComponent } from 'react';
import { Select, Spin } from 'antd';
import debounce from 'lodash.debounce';
import request from '../../../utils/request';

const { Option } = Select;

export default class Search extends PureComponent {
  constructor(props) {
    super(props);
    this.lastFetchId = 0;
    this.fetchServer = debounce(this.fetchServer, 800);
  }
  state = {
    data: [],
    fetching: false,
  }
  fetchServer = (value) => {
    if (!value || value.length < 1) {
      return;
    }
    const { url, query, variables = {} } = this.props;
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
        this.setState({ data: body.data[Object.keys(body.data)[0]], fetching: false });
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
        style={{ width: 400 }}
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
