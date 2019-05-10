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
import { connect } from 'dva';
import { Card, Input, Tabs, List, Avatar } from 'antd';
import moment from 'moment';
import { Panel } from '../../components/Page';
import styles from './Alarm.less';

const { Search } = Input;
const { TabPane } = Tabs;
const defaultPaging = {
  pageNum: 1,
  pageSize: 10,
  needTotal: true,
};

const funcMap = {
  "Service": "saveServiceAlarmList",
  "ServiceInstance": "saveServiceInstanceAlarmList",
  "Endpoint": "saveEndpointAlarmList",
}

@connect(state => ({
  alarm: state.alarm,
  globalVariables: state.global.globalVariables,
  loading: state.loading.models.alarm,
}))
export default class Alarm extends PureComponent {
  componentDidMount() {
    const {...propsData} = this.props;
    const { alarm: { variables: { values } } } = this.props;
    if (!values.scope) {
      propsData.dispatch({
        type: 'alarm/saveVariables',
        payload: { values: {
          scope: 'Service',
          paging: defaultPaging,
        } },
      });
    }
  }

  handleSearch = (keyword) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'alarm/saveVariables',
      payload: { values: {
        keyword,
        paging: defaultPaging,
      } },
    });
  }

  handlePageChange = (pag) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'alarm/saveVariables',
      payload: { values: {
        paging: {
          pageNum: pag,
          pageSize: 10,
          needTotal: true,
        },
      } },
    });
  }

  changeScope = (scope) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'alarm/saveVariables',
      payload: { values: {
        scope,
        paging: defaultPaging,
      } },
    });
  }

  handleChange = (variables) => {
    const {...propsData} = this.props;
    const { paging = defaultPaging } = variables;
    propsData.dispatch({
      type: 'alarm/fetchData',
      payload: { variables: { ...variables, paging }, reducer: funcMap[variables.scope] },
    });
  }

  renderList = ({ msgs, total }) => {
    const { alarm: { variables: { values: { paging = defaultPaging } } }, loading } = this.props;
    const pagination = {
      pageSize: paging.pageSize,
      current: paging.pageNum,
      total,
      onChange: this.handlePageChange,
    };
    return (
      <List
        className={styles.demoLoadmoreList}
        loading={loading}
        itemLayout="horizontal"
        dataSource={msgs}
        pagination={pagination}
        renderItem={msg => (
          <List.Item>
            <List.Item.Meta
              avatar={
                <Avatar
                  style={{ backgroundColor: '#b32400' }}
                  icon='close'
                />}
              description={msg.message}
            />
            <div>{moment(msg.startTime).format('YYYY-MM-DD HH:mm:ss')}</div>
          </List.Item>
        )}
      />);
  }

  render() {
    const extraContent = (
      <Search
        className={styles.extraContentSearch}
        placeholder="Search..."
        onSearch={this.handleSearch}
      />
    );
    const {...propsData} = this.props;
    const { alarm: { variables: { values }, data } } = this.props;
    return (
      <Panel
        variables={values}
        globalVariables={propsData.globalVariables}
        onChange={this.handleChange}
      >
        <Card
          title="Alarm List"
          bordered={false}
          extra={extraContent}
        >
          <Tabs activeKey={values.scope} onChange={this.changeScope}>
            <TabPane tab="Service" key="Service">{this.renderList(data.serviceAlarmList)}</TabPane>
            <TabPane tab="ServiceInstance" key="ServiceInstance">{this.renderList(data.serviceInstanceAlarmList)}</TabPane>
            <TabPane tab="Endpoint" key="Endpoint">{this.renderList(data.endpointAlarmList)}</TabPane>
          </Tabs>
        </Card>
      </Panel>
    );
  }
}
