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
import { Row, Col, Card, Tooltip, Icon } from 'antd';
import {
  ChartCard, HeatMap, Line,
} from '../../components/Charts';
import { generateDuration, axisMY } from '../../utils/time';
import { redirect } from '../../utils/utils';
import { Panel } from '../../components/Page';
import RankList from '../../components/RankList';

@connect(state => ({
  dashboard: state.dashboard,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
export default class Dashboard extends PureComponent {
  handleDurationChange = (variables) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'dashboard/fetchData',
      payload: { variables },
    });
  }

  renderAction = (prompt, path) => {
    const { history } = this.props;
    return (
      <Tooltip title={prompt}>
        <Icon type="info-circle-o" onClick={() => redirect(history, path)} />
      </Tooltip>
    );
  }

  render() {
    const { dashboard: { data }, globalVariables, duration, history } = this.props;
    return (
      <Panel globalVariables={globalVariables} onChange={this.handleDurationChange}>
        <Row gutter={8}>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="Service"
              action={this.renderAction('Show service details', '/monitor/service')}
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/app.png" alt="service" />}
              total={data.getGlobalBrief.numOfService}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="Endpoint"
              action={this.renderAction('Show endpoint details', '/monitor/endpoint')}
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/service.png" alt="endpoint" />}
              total={data.getGlobalBrief.numOfEndpoint}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="DB & Cache"
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/database.png" alt="database" />}
              total={data.getGlobalBrief.numOfDatabase
                + data.getGlobalBrief.numOfCache}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="MQ"
              avatar={<img style={{ width: 56, height: 56 }} src="img/icon/mq.png" alt="mq" />}
              total={data.getGlobalBrief.numOfMQ}
            />
          </Col>
        </Row>
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 8 }}>
            <ChartCard
              title="Calls HeatMap"
              contentHeight={200}
            >
              <HeatMap
                data={data.getThermodynamic}
                duration={duration}
                height={200}
                onClick={(d, responseTimeRange) => redirect(history, '/trace', { values: { duration: generateDuration({
                  from() {
                    return d.start;
                  },
                  to() {
                    return d.end;
                  },
                }),
                minTraceDuration: responseTimeRange.min,
                maxTraceDuration: responseTimeRange.max,
              } })}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 8 }}>
            <Card
              title="Response Time"
              bordered={false}
              bodyStyle={{ padding: 5, height: 150}}
            >
              <Line
                data={axisMY(duration, [{ title: 'p99', value: data.getP99}, { title: 'p95', value: data.getP95}
                , { title: 'p90', value: data.getP90}, { title: 'p75', value: data.getP75}, { title: 'p50', value: data.getP50}])}
              />
            </Card>
          </Col>
        </Row>
        <Row gutter={8}>
          <Col xs={24} sm={24} md={24} lg={16} xl={16} style={{ marginTop: 8 }}>
            <Card
              title="Slow Endpoint"
              bordered={false}
              bodyStyle={{ padding: '0px 10px' }}
            >
              <RankList
                data={data.getTopNSlowEndpoint}
                renderValue={_ => `${_.value} ms`}
                onClick={(key, { label }) => redirect(history, '/monitor/endpoint', { key, label })}
              />
            </Card>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 8 }}>
            <Card
              title="Service Throughput"
              bordered={false}
              bodyStyle={{ padding: '0px 10px' }}
            >
              <RankList
                data={data.getTopNServiceThroughput}
                renderValue={_ => `${_.value} cpm`}
                color="#965fe466"
                onClick={(key, { label }) => redirect(history, '/monitor/service', { key, label })}
              />
            </Card>
          </Col>
        </Row>
      </Panel>
    );
  }
}
