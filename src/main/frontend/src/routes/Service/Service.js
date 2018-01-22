import React, { Component } from 'react';
import { connect } from 'dva';
import { Row, Col, Select, Card } from 'antd';
import {
  ChartCard, MiniArea, MiniBar,
} from '../../components/Charts';
import { timeRange } from '../../utils/utils';
import { ServiceTopology } from '../../components/Topology';

const { Option } = Select;

@connect(state => ({
  service: state.service,
  duration: state.global.duration,
}))
export default class Service extends Component {
  shouldComponentUpdate(nextProps) {
    if (this.props.duration !== nextProps.duration) {
      this.props.dispatch({
        type: 'service/fetch',
        payload: {},
      });
    }
    return this.props.service !== nextProps.service;
  }
  handleChange(serviceId) {
    this.props.dispatch({
      type: 'service/fetch',
      payload: { serviceId },
    });
  }
  avg = list => (list.length > 0 ?
    (list.reduce((acc, curr) => acc + curr) / list.length).toFixed(2) : 0)
  render() {
    const { getServiceResponseTimeTrend, getServiceTPSTrend,
      getServiceSLATrend } = this.props.service;
    const timeRangeArray = timeRange(this.props.duration);
    return (
      <div>
        <Select
          showSearch
          style={{ width: 600 }}
          placeholder="Select a service"
          optionFilterProp="children"
          onChange={this.handleChange.bind(this)}
        >
          <Option value="Service1">Service1</Option>
          <Option value="Service2">Service2</Option>
          <Option value="Service3">Service3</Option>
        </Select>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg Throughout"
              total={`${this.avg(getServiceTPSTrend.trendList)}`}
            >
              <MiniArea
                animate={false}
                color="#975FE4"
                height={46}
                data={getServiceTPSTrend.trendList
                  .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg Response Time"
              total={`${this.avg(getServiceResponseTimeTrend.trendList)} ms`}
            >
              <MiniArea
                animate={false}
                height={46}
                data={getServiceResponseTimeTrend.trendList
                  .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg SLA"
              total={`${this.avg(getServiceSLATrend.trendList)} %`}
            >
              <MiniBar
                animate={false}
                height={46}
                data={getServiceSLATrend.trendList
                  .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
            <Card
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <ServiceTopology elements={this.props.service.getServiceTopology} layout={{ name: 'concentric', minNodeSpacing: 200 }} />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }
}
