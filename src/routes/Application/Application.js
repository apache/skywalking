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
import { Row, Col, Select, Card, Form, Breadcrumb } from 'antd';
import Server from './Server';
import { AppTopology } from '../../components/Topology';
import { Panel } from '../../components/Page';
import RankList from '../../components/RankList';
import ServerLitePanel from '../../components/ServerLitePanel';
import { getServerId, redirect } from '../../utils/utils';

const { Option } = Select;
const { Item: FormItem } = Form;

const middleColResponsiveProps = {
  xs: 24,
  sm: 24,
  md: 12,
  lg: 12,
  xl: 12,
  style: { marginTop: 8 },
};

@connect(state => ({
  application: state.application,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.application;
    return {
      applicationId: Form.createFormField({
        value: { key: values.applicationId ? values.applicationId : '', label: labels.applicationId ? labels.applicationId : '' },
      }),
    };
  },
})
export default class Application extends PureComponent {
  componentDidMount() {
    this.props.dispatch({
      type: 'application/initOptions',
      payload: { variables: this.props.globalVariables },
    });
  }
  componentWillUpdate(nextProps) {
    if (nextProps.globalVariables.duration === this.props.globalVariables.duration) {
      return;
    }
    this.props.dispatch({
      type: 'application/initOptions',
      payload: { variables: nextProps.globalVariables },
    });
  }
  handleSelect = (selected) => {
    this.props.dispatch({
      type: 'application/saveVariables',
      payload: {
        values: { applicationId: selected.key },
        labels: { applicationId: selected.label },
      },
    });
  }
  handleChange = (variables) => {
    const { data: { serverInfo, showServer } } = this.props.application;
    if (showServer) {
      this.handleSelectServer(serverInfo.key, serverInfo);
    } else {
      this.props.dispatch({
        type: 'application/fetchData',
        payload: { variables, reducer: 'saveApplication' },
      });
    }
  }
  handleGoApplication = () => {
    this.props.dispatch({
      type: 'application/hideServer',
    });
  }
  handleGoServer = () => {
    this.props.dispatch({
      type: 'application/showServer',
    });
  }
  handleSelectServer = (serverId, serverInfo) => {
    const { globalVariables: { duration } } = this.props;
    this.props.dispatch({
      type: 'application/fetchServer',
      payload: { variables: { duration, serverId }, serverInfo },
    });
  }
  renderApp = () => {
    const { getFieldDecorator } = this.props.form;
    const { variables: { values, options }, data } = this.props.application;
    return (
      <div>
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('applicationId')(
              <Select
                showSearch
                optionFilterProp="children"
                style={{ width: 200 }}
                placeholder="Select a application"
                labelInValue
                onSelect={this.handleSelect.bind(this)}
              >
                {options.applicationId && options.applicationId.map(app =>
                  <Option key={app.key} value={app.key}>{app.label}</Option>)}
              </Select>
            )}
          </FormItem>
        </Form>
        <Panel
          variables={values}
          globalVariables={this.props.globalVariables}
          onChange={this.handleChange}
        >
          <Row gutter={0}>
            <Col {...{ ...middleColResponsiveProps, xl: 16, lg: 12, md: 24 }}>
              <Card
                title="Application Map"
                bordered={false}
                bodyStyle={{ padding: 0 }}
              >
                <AppTopology
                  elements={data.getApplicationTopology}
                  height={335}
                  layout={{
                    name: 'dagre',
                    rankDir: 'LR',
                    minLen: 4,
                  }}
                />
              </Card>
            </Col>
            <Col {...{ ...middleColResponsiveProps, xl: 8, lg: 12, md: 24 }}>
              <Card
                bordered={false}
                bodyStyle={{ padding: '10px 10px', height: 391 }}
              >
                <ServerLitePanel
                  data={data}
                  serverList={data.getServerThroughput}
                  duration={this.props.duration}
                  onSelectServer={this.handleSelectServer}
                  onMoreServer={this.handleGoServer}
                />
              </Card>
            </Col>
          </Row>
          <Row gutter={8}>
            <Col {...{ ...middleColResponsiveProps, xl: 12, lg: 12, md: 24 }}>
              <Card
                title="Running Server"
                bordered={false}
                bodyStyle={{ padding: 5 }}
              >
                <RankList
                  data={data.getServerThroughput}
                  renderLabel={getServerId}
                  renderValue={_ => `${_.value} cpm`}
                  renderBadge={_ => ([
                    {
                      key: 'host',
                      label: 'Host',
                      value: _.host,
                    },
                    {
                      key: 'os',
                      label: 'OS',
                      value: _.osName,
                    },
                  ])}
                  color="#965fe466"
                />
              </Card>
            </Col>
            <Col {...{ ...middleColResponsiveProps, xl: 12, lg: 12, md: 24 }}>
              <Card
                title="Slow Service"
                bordered={false}
                bodyStyle={{ padding: '0px 10px' }}
              >
                <RankList
                  data={data.getSlowService}
                  renderValue={_ => `${_.value} ms`}
                  onClick={(key, item) => redirect(this.props.history, '/monitor/service', { key, label: item.label })}
                />
              </Card>
            </Col>
          </Row>
        </Panel>
      </div>
    );
  }
  render() {
    const { application, duration } = this.props;
    const { variables, data } = application;
    const { showServer, serverInfo } = data;
    return (
      <Row type="flex" justify="start">
        {showServer ? (
          <Col span={showServer ? 24 : 0}>
            <Breadcrumb>
              <Breadcrumb.Item>
                Application
              </Breadcrumb.Item>
              <Breadcrumb.Item>
                <a onClick={this.handleGoApplication}>{variables.labels.applicationId}</a>
              </Breadcrumb.Item>
              <Breadcrumb.Item>{getServerId(serverInfo)}</Breadcrumb.Item>
            </Breadcrumb>
            <Panel
              variables={variables.values}
              globalVariables={this.props.globalVariables}
              onChange={this.handleChange}
            >
              <Server data={data} duration={duration} />
            </Panel>
          </Col>
         ) : null}
        <Col span={showServer ? 0 : 24}>
          {this.renderApp()}
        </Col>
      </Row>
    );
  }
}
