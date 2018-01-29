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
    const { url, query } = this.props;
    this.lastFetchId += 1;
    const fetchId = this.lastFetchId;
    this.setState({ data: [], fetching: true });
    request(`/api${url}`, {
      method: 'POST',
      body: {
        variables: {
          keyword: value,
        },
        query,
      },
    })
      .then()
      .then((body) => {
        if (fetchId !== this.lastFetchId) { // for fetch callback order
          return;
        }
        this.setState({ data: body.data.searchServer, fetching: false });
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
            return (<Option value={_.key}>{_.label}</Option>);
          })}
      </Select>
    );
  }
}
