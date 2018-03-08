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
import { Row, Col, Card } from 'antd';
import {
  ChartCard, Pie, MiniArea, Field,
} from '../../components/Charts';
import { axis } from '../../utils/time';
import { avgTimeSeries } from '../../utils/utils';
import { Panel, Ranking } from '../../components/Page';

@connect(state => ({
  dashboard: state.dashboard,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
export default class Dashboard extends PureComponent {
  handleDurationChange = (variables) => {
    this.props.dispatch({
      type: 'dashboard/fetchData',
      payload: { variables },
    });
  }
  render() {
    const { data } = this.props.dashboard;
    const { numOfAlarmRate } = data.getAlarmTrend;
    const accuracy = 100;
    let visitData = [];
    let avg = 0;
    let max = 0;
    let min = 0;
    if (numOfAlarmRate && numOfAlarmRate.length > 0) {
      visitData = axis(this.props.duration, numOfAlarmRate, ({ x, y }) => ({ x, y: y / accuracy }));
      avg = avgTimeSeries(numOfAlarmRate) / accuracy;
      max = numOfAlarmRate.reduce((acc, curr) => { return acc < curr ? curr : acc; }) / accuracy;
      min = numOfAlarmRate.reduce((acc, curr) => { return acc > curr ? curr : acc; }) / accuracy;
    }
    return (
      <Panel globalVariables={this.props.globalVariables} onChange={this.handleDurationChange}>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="App"
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/app.svg" alt="app" />}
              total={data.getClusterBrief.numOfApplication}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="Service"
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/service.svg" alt="service" />}
              total={data.getClusterBrief.numOfService}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="DB & Cache"
              avatar={<img style={{ width: 64, height: 64 }} src="img/icon/database.png" alt="database" />}
              total={data.getClusterBrief.numOfDatabase
                + data.getClusterBrief.numOfCache}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="MQ"
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/mq.png" alt="mq" />}
              total={data.getClusterBrief.numOfMQ}
            />
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg Application Alarm"
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/alert.png" alt="app" />}
              total={`${avg.toFixed(2)}%`}
              footer={<div><Field label="Max" value={`${max}%`} /> <Field label="Min" value={`${min}%`} /></div>}
              contentHeight={100}
            >
              <MiniArea
                animate={false}
                color="#D87093"
                borderColor="#B22222"
                line="true"
                data={visitData}
                yAxis={{
                  formatter(val) {
                      return `${val} %`;
                  },
                }}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
            <ChartCard
              contentHeight={200}
            >
              <Pie
                hasLegend
                title="Database"
                subTitle="Total"
                total={data.getConjecturalApps.apps
                  .reduce((pre, now) => now.num + pre, 0)}
                data={data.getConjecturalApps.apps
                  .map((v) => { return { x: v.name, y: v.num }; })}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={16} xl={16} style={{ marginTop: 24 }}>
            <Card
              title="Slow Service"
              bordered={false}
              bodyStyle={{ padding: '0px 10px' }}
            >
              <Ranking data={data.getTopNSlowService} title="name" content="avgResponseTime" unit="ms" />
            </Card>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
            <Card
              title="Application Throughput"
              bordered={false}
              bodyStyle={{ padding: '0px 10px' }}
            >
              <Ranking data={data.getTopNApplicationThroughput} title="applicationCode" content="callsPerSec" unit="t/s" />
            </Card>
          </Col>
        </Row>
      </Panel>
    );
  }
}
