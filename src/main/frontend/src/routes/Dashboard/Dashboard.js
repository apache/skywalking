import React, { Component } from 'react';
import { connect } from 'dva';
import { Row, Col, Card, List, Avatar } from 'antd';
import {
  ChartCard, Pie, MiniArea, Field,
} from '../../components/Charts';
import { timeRange } from '../../utils/utils';

@connect(state => ({
  dashboard: state.dashboard,
  duration: state.global.duration,
}))
export default class Dashboard extends Component {
  componentDidMount() {
    this.props.dispatch({
      type: 'dashboard/fetch',
      payload: {},
    });
  }
  shouldComponentUpdate(nextProps) {
    if (this.props.duration !== nextProps.duration) {
      this.props.dispatch({
        type: 'dashboard/fetch',
        payload: {},
      });
    }
    return this.props.dashboard !== nextProps.dashboard;
  }
  renderList = (data, title, content) => {
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
                {item.key}
              </Avatar>}
            title={`${title}: ${item[title]}`}
            description={item[content]}
          />
        </List.Item>
      )}
    />);
  }
  render() {
    const visitData = [];
    const { numOfAlarmRate } = this.props.dashboard.getAlarmTrend;
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
      <div>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="App"
              avatar={<img style={{ width: 56, height: 56 }} src="app.svg" alt="app" />}
              total={this.props.dashboard.getClusterBrief.numOfApplication}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="Service"
              avatar={<img style={{ width: 56, height: 56 }} src="service.svg" alt="service" />}
              total={this.props.dashboard.getClusterBrief.numOfService}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="Store"
              avatar={<img style={{ width: 48, height: 56 }} src="database.svg" alt="database" />}
              total={this.props.dashboard.getClusterBrief.numOfDatabase
                + this.props.dashboard.getClusterBrief.numOfCache}
            />
          </Col>
          <Col xs={24} sm={24} md={12} lg={6} xl={6}>
            <ChartCard
              title="MQ"
              avatar={<img style={{ width: 56, height: 56 }} src="redis.svg" alt="redis" />}
              total={this.props.dashboard.getClusterBrief.numOfMQ}
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
                total={this.props.dashboard.getConjecturalApps.apps
                  .reduce((pre, now) => now.num + pre, 0)}
                data={this.props.dashboard.getConjecturalApps.apps
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
              bodyStyle={{ padding: 0 }}
            >
              {this.renderList(this.props.dashboard.getTopNSlowService, 'avgResponseTime', 'name')}
            </Card>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
            <Card
              title="Application Throughput"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              {this.renderList(this.props.dashboard.getTopNServerThroughput, 'tps', 'name')}
            </Card>
          </Col>
        </Row>
      </div>
    );
  }
}
