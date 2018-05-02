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
} from '../../components/Charts';
import DescriptionList from '../../components/DescriptionList';
import { axis } from '../../utils/time';
import { avgTimeSeries } from '../../utils/utils';

const { Description } = DescriptionList;


export default class Server extends PureComponent {
  bytesToMB = list => list.map(_ => parseFloat((_ / (1024 ** 2)).toFixed(2)))
  render() {
    const { duration, data } = this.props;
    const { serverInfo, getServerResponseTimeTrend, getServerThroughputTrend,
      getCPUTrend, getMemoryTrend, getGCTrend } = data;
    return (
      <div>
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={6} xl={6} style={{ marginTop: 8 }}>
            <Card style={{ marginTop: 8 }} bordered={false}>
              <DescriptionList col={1} layout="vertical" >
                <Description term="Host">{serverInfo.host}</Description>
                <Description term="IPv4">{serverInfo.ipv4 ? serverInfo.ipv4.join() : ''}</Description>
                <Description term="Pid">{serverInfo.pid}</Description>
                <Description term="OS">{serverInfo.osName}</Description>
              </DescriptionList>
            </Card>
          </Col>
          <Col xs={24} sm={24} md={24} lg={18} xl={18} style={{ marginTop: 8 }}>
            <Row gutter={8}>
              <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Avg Throughput"
                  total={`${avgTimeSeries(getServerThroughputTrend.trendList)} cpm`}
                  contentHeight={46}
                >
                  <MiniBar
                    color="#975FE4"
                    data={axis(duration, getServerThroughputTrend.trendList)}
                  />
                </ChartCard>
              </Col>
              <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Avg Response Time"
                  total={`${avgTimeSeries(getServerResponseTimeTrend.trendList)} ms`}
                  contentHeight={46}
                >
                  {getServerResponseTimeTrend.trendList.length > 0 ? (
                    <MiniArea
                      data={axis(duration, getServerResponseTimeTrend.trendList)}
                    />
                  ) : (<span style={{ display: 'none' }} />)}
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
                    data={axis(duration, getCPUTrend.cost)}
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
                    data={axis(duration, this.bytesToMB(getMemoryTrend.heap), ({ x, y }) => ({ x, y, type: 'value' }))
                      .concat(axis(duration, this.bytesToMB(getMemoryTrend.maxHeap), ({ x, y }) => ({ x, y, type: 'free' })))}
                  />
                </ChartCard>
              </Col>
              <Col span={24} style={{ marginTop: 8 }}>
                <ChartCard
                  title="Non-Heap MB"
                  contentHeight={150}
                >
                  <Area
                    data={axis(duration, this.bytesToMB(getMemoryTrend.noheap), ({ x, y }) => ({ x, y, type: 'value' }))
                    .concat(axis(duration, this.bytesToMB(getMemoryTrend.maxNoheap), ({ x, y }) => ({ x, y, type: 'free' })))}
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
                        <Tag color="#66b5ff" >
                          {getGCTrend.youngGCCount.reduce((sum, v) => sum + v)}
                        </Tag>
                        <span>collections</span>
                      </div>
                      <div>
                        <span style={{ marginRight: 10 }}>Old GC</span>
                        <Tag color="#ffb566" >
                          {getGCTrend.oldGCount.reduce((sum, v) => sum + v)}
                        </Tag>
                        <span>collections</span>
                      </div>
                    </div>
                  }
                >
                  <StackBar
                    data={axis(duration, getGCTrend.oldGCTime, ({ x, y }) => ({ x, y, type: 'oldGCTime' }))
                    .concat(axis(duration, getGCTrend.youngGCTime, ({ x, y }) => ({ x, y, type: 'youngGCTime' })))}
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
