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
import { Row, Col, Card, Tag } from 'antd';
import {
  ChartCard, MiniArea, MiniBar, Line, Area, StackBar,
} from 'components/Charts';
import DescriptionList from 'components/DescriptionList';
import { axisY } from '../../utils/time';
import { avgTS, getAttributes } from '../../utils/utils';

const { Description } = DescriptionList;


export default class ServiceInstance extends PureComponent {
  bytesToMB = list => list.map(_ => ({ value: parseFloat((_.value / (1024 ** 2)).toFixed(2))}))

  render() {
    const { duration, data } = this.props;
    const { serviceInstanceInfo, getServiceInstanceResponseTimeTrend, getServiceInstanceThroughputTrend, getServiceInstanceSLA,
      getCPUTrend, heap, maxHeap, noheap, maxNoheap, youngGCCount, oldGCCount, youngGCTime, oldGCTime } = data;
    const { attributes } = serviceInstanceInfo;
    return (
      <div>
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={6} xl={6} style={{ marginTop: 8 }}>
            <Card style={{ marginTop: 8 }} bordered={false}>
              <DescriptionList col={1} layout="vertical">
                <Description term="Host">{getAttributes(attributes, 'host_name')}</Description>
                <Description term="IPv4">{getAttributes(attributes, 'ipv4s')}</Description>
                <Description term="Pid">{getAttributes(attributes, 'process_no')}</Description>
                <Description term="OS">{getAttributes(attributes, 'os_name')}</Description>
              </DescriptionList>
            </Card>
          </Col>
          <Col xs={24} sm={24} md={24} lg={18} xl={18} style={{ marginTop: 8 }}>
            <Row gutter={8}>
              <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Avg Throughput"
                  total={`${avgTS(getServiceInstanceThroughputTrend.values)} cpm`}
                  contentHeight={46}
                >
                  <MiniBar
                    color="#975FE4"
                    data={axisY(duration, getServiceInstanceThroughputTrend.values)}
                  />
                </ChartCard>
              </Col>
              <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Avg Response Time"
                  total={`${avgTS(getServiceInstanceResponseTimeTrend.values)} ms`}
                  contentHeight={46}
                >
                  {getServiceInstanceResponseTimeTrend.values.length > 0 ? (
                    <MiniArea
                      data={axisY(duration, getServiceInstanceResponseTimeTrend.values)}
                    />
                  ) : (<span style={{ display: 'none' }} />)}
                </ChartCard>
              </Col>
              <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Avg SLA"
                  total={`${(avgTS(getServiceInstanceSLA.values) / 100).toFixed(2)} %`}
                >
                  <MiniBar
                    animate={false}
                    height={46}
                    data={axisY(duration, getServiceInstanceSLA.values,
                      ({ x, y }) => ({ x, y: y / 100 }))}
                  />
                </ChartCard>
              </Col>
            </Row>
            <Row gutter={8}>
              <Col span={24} style={{ marginTop: 8 }}>
                <ChartCard
                  title="CPU %"
                  contentHeight={150}
                >
                  <Line
                    data={axisY(duration, getCPUTrend.values)}
                  />
                </ChartCard>
              </Col>
            </Row>
            <Row gutter={8}>
              <Col span={24} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Heap MB"
                  contentHeight={150}
                >
                  <Area
                    data={axisY(duration, this.bytesToMB(heap.values), ({ x, y }) => ({ x, y, type: 'value' }))
                      .concat(axisY(duration, this.bytesToMB(maxHeap.values), ({ x, y }) => ({ x, y, type: 'free' })))}
                  />
                </ChartCard>
              </Col>
              <Col span={24} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Non-Heap MB"
                  contentHeight={150}
                >
                  <Area
                    data={axisY(duration, this.bytesToMB(noheap.values), ({ x, y }) => ({ x, y, type: 'value' }))
                    .concat(axisY(duration, this.bytesToMB(maxNoheap.values), ({ x, y }) => ({ x, y, type: 'free' })))}
                  />
                </ChartCard>
              </Col>
            </Row>
            <Row gutter={8}>
              <Col span={24} style={{ marginTop: 8 }}>
                <ChartCard
                  title="GC ms"
                  contentHeight={150}
                  footer={
                    <div>
                      <div style={{ marginBottom: 10 }}>
                        <span style={{ marginRight: 10 }}>Young GC</span>
                        <Tag color="#66b5ff">
                          {youngGCCount.values.map(_ => _.value).reduce((sum, v) => sum + v)}
                        </Tag>
                        <span>collections</span>
                      </div>
                      <div>
                        <span style={{ marginRight: 10 }}>Old GC</span>
                        <Tag color="#ffb566">
                          {oldGCCount.values.map(_ => _.value).reduce((sum, v) => sum + v)}
                        </Tag>
                        <span>collections</span>
                      </div>
                    </div>
                  }
                >
                  <StackBar
                    data={axisY(duration, youngGCTime.values, ({ x, y }) => ({ x, y, type: 'youngGCTime' }))
                    .concat(axisY(duration, oldGCTime.values, ({ x, y }) => ({ x, y, type: 'oldGCTime' })))}
                  />
                </ChartCard>
              </Col>
            </Row>
          </Col>
        </Row>
      </div>
    );
  }
}
