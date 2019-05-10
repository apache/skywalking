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
import { AppTopology } from 'components/Topology';
import { Line, ChartCard, MiniArea, MiniBar } from 'components/Charts';
import { Panel } from 'components/Page';
import RankList from 'components/RankList';
import ServiceInstanceLitePanel from 'components/ServiceInstanceLitePanel';
import ServiceInstance from './ServiceInstance';
import { getServiceInstanceId, redirect, avgTS } from '../../utils/utils';
import { axisY, axisMY } from '../../utils/time';

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
  service: state.service,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.service;
    return {
      serviceId: Form.createFormField({
        value: { key: values.serviceId ? values.serviceId : '', label: labels.serviceId ? labels.serviceId : '' },
      }),
    };
  },
})
export default class Service extends PureComponent {
  componentDidMount() {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'service/initOptions',
      payload: { variables: propsData.globalVariables },
    });
  }

  componentWillUpdate(nextProps) {
    const {...propsData} = this.props;
    if (nextProps.globalVariables.duration === propsData.globalVariables.duration) {
      return;
    }
    propsData.dispatch({
      type: 'service/initOptions',
      payload: { variables: nextProps.globalVariables },
    });
  }

  handleSelect = (selected) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'service/saveVariables',
      payload: {
        values: { serviceId: selected.key },
        labels: { serviceId: selected.label },
      },
    });
  }

  handleChange = (variables) => {
    const {...propsData} = this.props;
    const { data: { serviceInstanceInfo, showServiceInstance } } = propsData.service;
    if (showServiceInstance) {
      this.handleSelectServiceInstance(serviceInstanceInfo.key, serviceInstanceInfo);
    } else {
      propsData.dispatch({
        type: 'service/fetchData',
        payload: { variables, reducer: 'saveService' },
      });
    }
  }

  handleGoService = () => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'service/hideServiceInstance',
    });
  }

  handleGoServiceInstance = () => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'service/showServiceInstance',
    });
  }

  handleSelectServiceInstance = (serviceInstanceId, serviceInstanceInfo) => {
    const {...propsData} = this.props;
    const { globalVariables: { duration } } = this.props;
    propsData.dispatch({
      type: 'service/fetchServiceInstance',
      payload: { variables: { duration, serviceInstanceId }, serviceInstanceInfo },
    });
  }

  renderApp = () => {
    const {...propsData} = this.props;
    const { duration } = this.props;
    const { getFieldDecorator } = propsData.form;
    const { variables: { values, options, labels }, data } = propsData.service;
    const { getResponseTimeTrend, getThroughputTrend, getSLATrend } = data;
    return (
      <div>
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('serviceId')(
              <Select
                showSearch
                optionFilterProp="children"
                style={{ minWidth: 250, maxWidth: 400 }}
                placeholder="Select a service"
                labelInValue
                onSelect={this.handleSelect.bind(this)}
              >
                {options.serviceId && options.serviceId.map(service =>
                  <Option key={service.key} value={service.key}>{service.label}</Option>)}
              </Select>
            )}
          </FormItem>
        </Form>
        <Panel
          variables={values}
          globalVariables={propsData.globalVariables}
          onChange={this.handleChange}
        >
          <Row gutter={0}>
            <Col {...{ ...middleColResponsiveProps, xl: 16, lg: 12, md: 24 }}>
              <Card
                title="Service Map"
                bordered={false}
                bodyStyle={{ padding: 0 }}
              >
                <AppTopology
                  elements={data.getServiceTopology}
                  height={460}
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
                bodyStyle={{ padding: '10px 10px', height: 516 }}
              >
                <ServiceInstanceLitePanel
                  data={data}
                  serviceInstanceList={data.getServiceInstances}
                  duration={propsData.duration}
                  onSelectServiceInstance={this.handleSelectServiceInstance}
                  onMoreServiceInstance={this.handleGoServiceInstance}
                />
              </Card>
            </Col>
          </Row>
          <Row gutter={8}>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
              <ChartCard
                title="Avg Throughput"
                total={`${avgTS(getThroughputTrend.values)} cpm`}
                contentHeight={46}
              >
                <MiniArea
                  color="#975FE4"
                  data={axisY(duration, getThroughputTrend.values)}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
              <ChartCard
                title="Avg Response Time"
                total={`${avgTS(getResponseTimeTrend.values)} ms`}
                contentHeight={46}
              >
                <MiniArea
                  data={axisY(duration, getResponseTimeTrend.values)}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
              <ChartCard
                title="Avg SLA"
                total={`${(avgTS(getSLATrend.values) / 100).toFixed(2)} %`}
                contentHeight={46}
              >
                <MiniBar
                  animate={false}
                  data={axisY(duration, getSLATrend.values,
                    ({ x, y }) => ({ x, y: y / 100 }))}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row>
            <Col {...{ ...middleColResponsiveProps, xl: 24, lg: 24, md: 24 }}>
              <Card
                title="Response Time"
                bordered={false}
                bodyStyle={{ padding: 5, height: 150}}
              >
                <Line
                  data={axisMY(propsData.duration, [{ title: 'p99', value: data.getP99}, { title: 'p95', value: data.getP95}
                  , { title: 'p90', value: data.getP90}, { title: 'p75', value: data.getP75}, { title: 'p50', value: data.getP50}])}
                />
              </Card>
            </Col>
          </Row>
          <Row gutter={8}>
            <Col {...{ ...middleColResponsiveProps, xl: 12, lg: 12, md: 24 }}>
              <Card
                title="Running ServiceInstance"
                bordered={false}
                bodyStyle={{ padding: 5 }}
              >
                <RankList
                  data={data.getServiceInstanceThroughput}
                  renderValue={_ => `${_.value} cpm`}
                  color="#965fe466"
                />
              </Card>
            </Col>
            <Col {...{ ...middleColResponsiveProps, xl: 12, lg: 12, md: 24 }}>
              <Card
                title="Slow Endpoint"
                bordered={false}
                bodyStyle={{ padding: '0px 10px' }}
              >
                <RankList
                  data={data.getSlowEndpoint}
                  renderValue={_ => `${_.value} ms`}
                  onClick={(key, item) => redirect(propsData.history, '/monitor/endpoint', { key,
                    label: item.label,
                    serviceId: values.serviceId,
                    serviceName: labels.serviceId })}
                />
              </Card>
            </Col>
          </Row>
        </Panel>
      </div>
    );
  }

  render() {
    const { globalVariables, service, duration } = this.props;
    const { variables, data } = service;
    const { showServiceInstance, serviceInstanceInfo } = data;
    return (
      <Row type="flex" justify="start">
        {showServiceInstance ? (
          <Col span={showServiceInstance ? 24 : 0}>
            <Breadcrumb>
              <Breadcrumb.Item>
                Service
              </Breadcrumb.Item>
              <Breadcrumb.Item>
                <a onClick={this.handleGoService}>{variables.labels.serviceId}</a>
              </Breadcrumb.Item>
              <Breadcrumb.Item>{getServiceInstanceId(serviceInstanceInfo)}</Breadcrumb.Item>
            </Breadcrumb>
            <Panel
              variables={variables.values}
              globalVariables={globalVariables}
              onChange={this.handleChange}
            >
              <ServiceInstance data={data} duration={duration} />
            </Panel>
          </Col>
         ) : null}
        <Col span={showServiceInstance ? 0 : 24}>
          {this.renderApp()}
        </Col>
      </Row>
    );
  }
}
