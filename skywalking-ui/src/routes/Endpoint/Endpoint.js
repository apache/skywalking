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
import { Row, Col, Form, Button, Icon, Select } from 'antd';
import {
  ChartCard, MiniArea, MiniBar, Line, EndpointDeps,
} from 'components/Charts';
import { axisY, axisMY } from '../../utils/time';
import { avgTS } from '../../utils/utils';
import { Panel, Search } from '../../components/Page';
import TraceList from '../../components/Trace/TraceList';
import TraceTimeline from '../Trace/TraceTimeline';

const { Item: FormItem } = Form;
const { Option } = Select;

@connect(state => ({
  endpoint: state.endpoint,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
  loading: state.loading.models.endpoint,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.endpoint;
    return {
      serviceId: Form.createFormField({
        value: { key: values.serviceId ? values.serviceId : '', label: labels.serviceId ? labels.serviceId : '' },
      }),
      endpointId: Form.createFormField({
        value: { key: values.endpointId ? values.endpointId : '', label: labels.endpointId ? labels.endpointId : '' },
      }),
    };
  },
})
export default class Endpoint extends PureComponent {
  componentDidMount() {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'endpoint/initOptions',
      payload: { variables: propsData.globalVariables, reducer: 'saveServiceInfo' },
    });
  }

  componentWillUpdate(nextProps) {
    const {...propsData} = this.props;
    if (nextProps.globalVariables.duration === propsData.globalVariables.duration) {
      return;
    }
    propsData.dispatch({
      type: 'endpoint/initOptions',
      payload: { variables: nextProps.globalVariables, reducer: 'saveServiceInfo' },
    });
  }

  handleServiceSelect = (selected) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'endpoint/save',
      payload: {
        variables: {
          values: { serviceId: selected.key, endpointId: null },
          labels: { serviceId: selected.label, endpointId: null },
        },
        data: {
          serviceInfo: { serviceId: selected.key },
        },
      },
    });
  }

  handleSelect = (selected) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'endpoint/save',
      payload: {
        variables: {
          values: { endpointId: selected.key },
          labels: { endpointId: selected.label },
        },
        data: {
          endpointInfo: selected,
        },
      },
    });
  }

  handleChange = (variables) => {
    const {...propsData} = this.props;
    const { variables: { values } } = propsData.endpoint;
    if (!values.serviceId) {
      return;
    }
    const { key: endpointId, label: endpointName, duration } = variables;
    if (!endpointId) {
      return;
    }
    propsData.dispatch({
      type: 'endpoint/fetchData',
      payload: { variables: {
        endpointId,
        duration,
        traceCondition: {
          endpointId: values.endpointId,
          endpointName,
          queryDuration: duration,
          traceState: 'ALL',
          queryOrder: 'BY_DURATION',
          paging: {
            pageNum: 1,
            pageSize: 20,
            needTotal: false,
          },
        },
      } },
    });
  }

  handleShowTrace = (traceId) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'endpoint/fetchSpans',
      payload: { variables: { traceId } },
    });
  }

  handleGoBack = () => {
    const { dispatch } = this.props;
    dispatch({
      type: 'endpoint/hideTimeline',
    });
  }

  handleLoadMetrics = ({ calls }) => {
    const { dispatch, globalVariables: { duration } } = this.props;
    dispatch({
      type: 'endpoint/fetchMetrics',
      payload: { variables: {
        idsS: calls.filter(_ => _.detectPoint === 'SERVER').map(_ => _.id),
        idsC: calls.filter(_ => _.detectPoint === 'CLIENT').map(_ => _.id),
        duration,
      }},
    });
  }

  edgeWith = edge => edge.cpm;

  renderPanel = () => {
    const {...propsData} = this.props;
    const { endpoint, duration } = this.props;
    const { variables: { values }, data } = endpoint;
    const { getEndpointResponseTimeTrend, getEndpointThroughputTrend,
      getEndpointSLATrend, queryBasicTraces } = data;
    if (!values.endpointId) {
      return null;
    }
    return (
      <Panel
        variables={data.endpointInfo}
        globalVariables={propsData.globalVariables}
        onChange={this.handleChange}
      >
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
            <ChartCard
              title="Avg Throughput"
              total={`${avgTS(getEndpointThroughputTrend.values)} cpm`}
              contentHeight={46}
            >
              <MiniArea
                color="#975FE4"
                data={axisY(duration, getEndpointThroughputTrend.values)}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
            <ChartCard
              title="Avg Response Time"
              total={`${avgTS(getEndpointResponseTimeTrend.values)} ms`}
              contentHeight={46}
            >
              <MiniArea
                data={axisY(duration, getEndpointResponseTimeTrend.values)}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
            <ChartCard
              title="Avg SLA"
              total={`${(avgTS(getEndpointSLATrend.values) / 100).toFixed(2)} %`}
            >
              <MiniBar
                animate={false}
                height={46}
                data={axisY(duration, getEndpointSLATrend.values,
                  ({ x, y }) => ({ x, y: y / 100 }))}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 8 }}>
            <ChartCard
              title="Response Time"
            >
              <Line
                height={150}
                data={axisMY(propsData.duration, [{ title: 'p99', value: data.getP99}, { title: 'p95', value: data.getP95}
                , { title: 'p90', value: data.getP90}, { title: 'p75', value: data.getP75}, { title: 'p50', value: data.getP50}])}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 8 }}>
            <ChartCard
              title="Dependency Map"
              contentHeight={200}
            >
              <EndpointDeps
                deps={data.getEndpointTopology}
                metrics={data.metrics}
                onLoadMetrics={this.handleLoadMetrics}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 8 }}>
            <ChartCard
              title="Top 20 Slow Traces"
            >
              <TraceList
                data={queryBasicTraces.traces}
                onClickTraceTag={this.handleShowTrace}
                loading={propsData.loading}
              />
            </ChartCard>
          </Col>
        </Row>
      </Panel>
    );
  }

  render() {
    const { form, endpoint } = this.props;
    const { getFieldDecorator } = form;
    const { variables: { options }, data } = endpoint;
    const { showTimeline, queryTrace, currentTraceId } = data;
    if (!this.serviceInfo) {
      this.serviceInfo = data.serviceInfo;
    }
    if (data.serviceInfo && this.serviceInfo.serviceId !== data.serviceInfo.serviceId) {
      this.serviceInfo = data.serviceInfo;
    }
    return (
      <div>
        {showTimeline ? (
          <Row type="flex" justify="start">
            <Col style={{ marginBottom: 24 }}>
              <Button ghost type="primary" size="small" onClick={() => { this.handleGoBack(); }}>
                <Icon type="left" />Go back
              </Button>
            </Col>
          </Row>
      ) : null}
        <Row type="flex" justify="start">
          <Col span={showTimeline ? 0 : 24}>
            <Form layout="inline">
              <FormItem>
                {getFieldDecorator('serviceId')(
                  <Select
                    showSearch
                    optionFilterProp="children"
                    style={{ minWidth: 250, maxWidth: 400 }}
                    placeholder="Select a service"
                    labelInValue
                    onSelect={this.handleServiceSelect.bind(this)}
                  >
                    {options.serviceId && options.serviceId.map(service =>
                      <Option key={service.key} value={service.key}>{service.label}</Option>)}
                  </Select>
                )}
              </FormItem>
              {this.serviceInfo && this.serviceInfo.serviceId  ? (
                <FormItem>
                  {getFieldDecorator('endpointId')(
                    <Search
                      placeholder="Search a endpoint"
                      onSelect={this.handleSelect.bind(this)}
                      url="/graphql"
                      variables={this.serviceInfo}
                      query={`
                        query SearchEndpoint($serviceId: ID!, $keyword: String!) {
                          searchEndpoint(serviceId: $serviceId, keyword: $keyword, limit: 10) {
                            key: id
                            label: name
                          }
                        }
                      `}
                    />
                  )}
                </FormItem>
              ) : null}
            </Form>
            {this.renderPanel()}
          </Col>
          <Col span={showTimeline ? 24 : 0}>
            {showTimeline ? (
              <TraceTimeline
                trace={{ data: { queryTrace, currentTraceId } }}
              />
            ) : null}
          </Col>
        </Row>
      </div>
    );
  }
}
