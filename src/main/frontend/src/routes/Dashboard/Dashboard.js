import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Card, List, Avatar } from 'antd';
import {
  ChartCard, Pie, MiniArea, Field,
} from '../../components/Charts';
import { timeRange } from '../../utils/utils';
import { Panel } from '../../components/Page';

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
  renderList = (data, title, content, unit) => {
    let index = 0;
    return (<List
      size="small"
      itemLayout="horizontal"
      dataSource={data}
      renderItem={item => (
        <List.Item>
          <List.Item.Meta
            avatar={
              <Avatar
                style={{ color: '#ff3333', backgroundColor: '#ffb84d' }}
              >
                {(() => { index += 1; return index; })()}
              </Avatar>}
            title={item[title]}
            description={`${item[content]} ${unit}`}
          />
        </List.Item>
      )}
    />);
  }
  render() {
    const { data } = this.props.dashboard;
    const visitData = [];
    const { numOfAlarmRate } = data.getAlarmTrend;
    let avg = 0;
    let max = 0;
    let min = 0;
    if (numOfAlarmRate && numOfAlarmRate.length > 0) {
      timeRange(this.props.duration).forEach((v, i) => {
        visitData.push({ x: v, y: numOfAlarmRate[i] });
      });
      avg = numOfAlarmRate.reduce((acc, curr) => acc + curr) / numOfAlarmRate.length;
      max = numOfAlarmRate.reduce((acc, curr) => { return acc < curr ? curr : acc; });
      min = numOfAlarmRate.reduce((acc, curr) => { return acc > curr ? curr : acc; });
    }
    return (
      <Panel globalVariables={this.props.globalVariables} onChange={this.handleDurationChange}>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="App"
              avatar={<img style={{ width: 56, height: 56 }} src="app.svg" alt="app" />}
              total={data.getClusterBrief.numOfApplication}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="Service"
              avatar={<img style={{ width: 56, height: 56 }} src="service.svg" alt="service" />}
              total={data.getClusterBrief.numOfService}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="Store"
              avatar={<img style={{ width: 48, height: 56 }} src="database.svg" alt="database" />}
              total={data.getClusterBrief.numOfDatabase
                + data.getClusterBrief.numOfCache}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="MQ"
              avatar={<img style={{ width: 56, height: 56 }} src="redis.svg" alt="redis" />}
              total={data.getClusterBrief.numOfMQ}
            />
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg Application Alarm"
              avatar={<img style={{ width: 56, height: 56 }} src="alert.svg" alt="app" />}
              total={`${avg.toFixed(2)}%`}
              footer={<div><Field label="Max" value={`${max}%`} /> <Field label="Min" value={`${min}%`} /></div>}
            >
              <MiniArea
                animate={false}
                color="#D87093"
                borderColor="#B22222"
                line="true"
                height={143}
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
            <Card
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Pie
                animate={false}
                hasLegend
                title="Database"
                subTitle="Total"
                total={data.getConjecturalApps.apps
                  .reduce((pre, now) => now.num + pre, 0)}
                data={data.getConjecturalApps.apps
                  .map((v) => { return { x: v.name, y: v.num }; })}
                height={300}
                lineWidth={4}
              />
            </Card>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={16} xl={16} style={{ marginTop: 24 }}>
            <Card
              title="Slow Service"
              bordered={false}
              bodyStyle={{ padding: 10 }}
            >
              {this.renderList(data.getTopNSlowService, 'name', 'avgResponseTime', 'ms')}
            </Card>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
            <Card
              title="Application Throughput"
              bordered={false}
              bodyStyle={{ padding: 10 }}
            >
              {this.renderList(data.getTopNApplicationThroughput, 'applicationCode', 'tps', 't/s')}
            </Card>
          </Col>
        </Row>
      </Panel>
    );
  }
}
