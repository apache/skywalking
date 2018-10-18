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
  ChartCard, MiniArea, MiniBar, Sankey,
} from '../../components/Charts';
import { axis } from '../../utils/time';
import { avgTimeSeries } from '../../utils/utils';
import { Panel, Search } from '../../components/Page';
import TraceList from '../../components/Trace/TraceList';
import TraceTimeline from '../Trace/TraceTimeline';

const { Item: FormItem } = Form;
const { Option } = Select;

@connect(state => ({
  service: state.service,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
  loading: state.loading.models.service,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.service;
    return {
      applicationId: Form.createFormField({
        value: { key: values.applicationId ? values.applicationId : '', label: labels.applicationId ? labels.applicationId : '' },
      }),
      serviceId: Form.createFormField({
        value: { key: values.serviceId ? values.serviceId : '', label: labels.serviceId ? labels.serviceId : '' },
      }),
    };
  },
})
export default class Service extends PureComponent {
  componentDidMount() {
    this.props.dispatch({
      type: 'service/initOptions',
      payload: { variables: this.props.globalVariables, reducer: 'saveAppInfo' },
    });
  }
  componentWillUpdate(nextProps) {
    if (nextProps.globalVariables.duration === this.props.globalVariables.duration) {
      return;
    }
    this.props.dispatch({
      type: 'service/initOptions',
      payload: { variables: nextProps.globalVariables, reducer: 'saveAppInfo' },
    });
  }
  handleAppSelect = (selected) => {
    this.props.dispatch({
      type: 'service/save',
      payload: {
        variables: {
          values: { applicationId: selected.key, serviceId: null },
          labels: { applicationId: selected.label, serviceId: null },
        },
        data: {
          appInfo: { applicationId: selected.key },
        },
      },
    });
  }
  handleSelect = (selected) => {
    this.props.dispatch({
      type: 'service/save',
      payload: {
        variables: {
          values: { serviceId: selected.key },
          labels: { serviceId: selected.label },
        },
        data: {
          serviceInfo: selected,
        },
      },
    });
  }
  handleChange = (variables) => {
    const { variables: { values } } = this.props.service;
    if (!values.applicationId) {
      return;
    }
    const { key: serviceId, label: serviceName, duration } = variables;
    if (!serviceId) {
      return;
    }
    this.props.dispatch({
      type: 'service/fetchData',
      payload: { variables: {
        serviceId,
        duration,
        traceCondition: {
          applicationId: values.applicationId,
          operationName: serviceName,
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
      type: 'service/fetchSpans',
      payload: { variables: { traceId } },
    });
  }
  handleGoBack = () => {
    this.props.dispatch({
      type: 'service/hideTimeline',
    });
  }
  edgeWith = edge => edge.cpm * edge.avgResponseTime;
  renderPanel = () => {
    const { service, duration } = this.props;
    const { variables: { values }, data } = service;
    const { getServiceResponseTimeTrend, getServiceThroughputTrend,
      getServiceSLATrend, getServiceTopology, queryBasicTraces } = data;
    if (!values.serviceId) {
      return null;
    }
    return (
      <Panel
        variables={data.serviceInfo}
        globalVariables={this.props.globalVariables}
        onChange={this.handleChange}
      >
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
            <ChartCard
              title="Avg Throughput"
              total={`${avgTimeSeries(getServiceThroughputTrend.trendList)} cpm`}
              contentHeight={46}
            >
              <MiniArea
                color="#975FE4"
                data={axis(duration, getServiceThroughputTrend.trendList)}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
            <ChartCard
              title="Avg Response Time"
              total={`${avgTimeSeries(getServiceResponseTimeTrend.trendList)} ms`}
              contentHeight={46}
            >
              <MiniArea
                data={axis(duration, getServiceResponseTimeTrend.trendList)}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
            <ChartCard
              title="Avg SLA"
              total={`${(avgTimeSeries(getServiceSLATrend.trendList) / 100).toFixed(2)} %`}
            >
              <MiniBar
                animate={false}
                height={46}
                data={axis(duration, getServiceSLATrend.trendList,
                  ({ x, y }) => ({ x, y: y / 100 }))}
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
                loading={this.props.loading}
              />
            </ChartCard>
          </Col>
        </Row>
        {this.renderSankey(getServiceTopology)}
      </Panel>
    );
  }
  renderSankey = (data) => {
    if (data.nodes.length < 2) {
      return <span style={{ display: 'none' }} />;
    }
    const nodesMap = new Map();
    data.nodes.forEach((_, i) => {
      nodesMap.set(`${_.id}`, i);
    });
    const nData = {
      nodes: data.nodes,
      edges: data.calls
        .filter(_ => nodesMap.has(`${_.source}`) && nodesMap.has(`${_.target}`))
        .map(_ =>
          ({ ..._, value: (this.edgeWith(_) < 1 ? 1000 : this.edgeWith(_)), source: nodesMap.get(`${_.source}`), target: nodesMap.get(`${_.target}`) })),
    };
    return (
      <Row gutter={8}>
        <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 8 }}>
          <ChartCard
            title="Dependency Map"
            contentHeight={200}
          >
            <Sankey
              data={nData}
              edgeTooltip={['target*source*cpm*avgResponseTime*isAlert', (target, source, cpm, avgResponseTime) => {
                return {
                  name: `${source.name} to ${target.name} </span>`,
                  value: `${cpm < 1 ? '<1' : cpm} cpm ${avgResponseTime}ms`,
                };
              }]}
              edgeColor={['isAlert', isAlert => (isAlert ? '#DC143C' : '#bbb')]}
            />
          </ChartCard>
        </Col>
      </Row>);
  }
  render() {
    const { form, service } = this.props;
    const { getFieldDecorator } = form;
    const { variables: { options }, data } = service;
    const { showTimeline, queryTrace, currentTraceId } = data;
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
                {getFieldDecorator('applicationId')(
                  <Select
                    showSearch
                    optionFilterProp="children"
                    style={{ width: 200 }}
                    placeholder="Select a application"
                    labelInValue
                    onSelect={this.handleAppSelect.bind(this)}
                  >
                    {options.applicationId && options.applicationId.map(app =>
                      <Option key={app.key} value={app.key}>{app.label}</Option>)}
                  </Select>
                )}
              </FormItem>
              {data.appInfo ? (
                <FormItem>
                  {getFieldDecorator('serviceId')(
                    <Search
                      placeholder="Search a service"
                      onSelect={this.handleSelect.bind(this)}
                      url="/service/search"
                      variables={data.appInfo}
                      query={`
                        query SearchService($applicationId: ID!, $keyword: String!) {
                          searchService(applicationId: $applicationId, keyword: $keyword, topN: 10) {
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
