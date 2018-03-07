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
import { Row, Col, Form } from 'antd';
import {
  ChartCard, MiniArea, MiniBar, Sankey,
} from '../../components/Charts';
import { axis } from '../../utils/time';
import { Panel, Search } from '../../components/Page';

const { Item: FormItem } = Form;

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
    this.props.dispatch({
      type: 'service/fetchData',
      payload: { variables },
    });
  }
  avg = list => (list.length > 0 ?
    (list.reduce((acc, curr) => acc + curr) / list.length).toFixed(2) : 0)
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
      edges: data.calls.map(_ =>
        ({ ..._, value: (_.callsPerSec < 1 ? 1000 : _.callsPerSec * _.avgResponseTime), source: nodesMap.get(`${_.source}`), target: nodesMap.get(`${_.target}`) })),
    };
    return (
      <Row gutter={24}>
        <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
          <ChartCard
            title="Dependency Map"
            contentHeight={200}
          >
            <Sankey
              data={nData}
              edgeTooltip={['target*source*callsPerSec*avgResponseTime*isAlert', (target, source, callsPerSec, avgResponseTime) => {
                return {
                  name: `${source.name} to ${target.name} </span>`,
                  value: `${callsPerSec < 1 ? '<1' : callsPerSec} calls/s ${avgResponseTime}ms`,
                };
              }]}
              edgeColor={['isAlert', isAlert => (isAlert ? '#DC143C' : '#bbb')]}
            />
          </ChartCard>
        </Col>
      </Row>);
  }
  render() {
    const { form, service, duration } = this.props;
    const { getFieldDecorator } = form;
    const { variables: { values }, data } = service;
    const { getServiceResponseTimeTrend, getServiceTPSTrend,
      getServiceSLATrend, getServiceTopology } = data;
    return (
      <div>
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('serviceId')(
              <Search
                placeholder="Search a service"
                onSelect={this.handleSelect.bind(this)}
                url="/service/search"
                query={`
                  query SearchService($keyword: String!) {
                    searchService(keyword: $keyword, topN: 10) {
                      key: id
                      label: name
                    }
                  }
                `}
              />
            )}
          </FormItem>
        </Form>
        <Panel
          variables={values}
          globalVariables={this.props.globalVariables}
          onChange={this.handleChange}
        >
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Throughout"
                total={`${this.avg(getServiceTPSTrend.trendList)}`}
                contentHeight={46}
              >
                <MiniArea
                  color="#975FE4"
                  data={axis(duration, getServiceTPSTrend.trendList)}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Response Time"
                total={`${this.avg(getServiceResponseTimeTrend.trendList)} ms`}
                contentHeight={46}
              >
                <MiniArea
                  data={axis(duration, getServiceResponseTimeTrend.trendList)}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg SLA"
                total={`${this.avg(getServiceSLATrend.trendList) / 100} %`}
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
          {this.renderSankey(getServiceTopology)}
        </Panel>
      </div>
    );
  }
}
