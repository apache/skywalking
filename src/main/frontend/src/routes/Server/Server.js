import React, { Component } from 'react';
import { connect } from 'dva';
import { Row, Col, Select, Card } from 'antd';
import {
  ChartCard, MiniArea, MiniBar, Line, Area,
} from '../../components/Charts';
import DescriptionList from '../../components/DescriptionList';
import { timeRange } from '../../utils/utils';

const { Description } = DescriptionList;
const { Option } = Select;

@connect(state => ({
  server: state.server,
  duration: state.global.duration,
}))
export default class Server extends Component {
  shouldComponentUpdate(nextProps) {
    if (this.props.duration !== nextProps.duration) {
      this.props.dispatch({
        type: 'server/fetch',
        payload: {},
      });
    }
    return this.props.server !== nextProps.server;
  }
  handleChange(serverId) {
    this.props.dispatch({
      type: 'server/fetch',
      payload: { serverId },
    });
  }
  avg = list => (list.length > 0 ?
    (list.reduce((acc, curr) => acc + curr) / list.length).toFixed(2) : 0)
  render() {
    const { getServerResponseTimeTrend, getServerTPSTrend,
      getCPUTrend, getMemoryTrend, getGCTrend } = this.props.server;
    const timeRangeArray = timeRange(this.props.duration);
    return (
      <div>
        <Select
          showSearch
          style={{ width: 200 }}
          placeholder="Select a server"
          optionFilterProp="children"
          onChange={this.handleChange.bind(this)}
        >
          <Option value="Server1">Server1</Option>
          <Option value="Server2">Server2</Option>
          <Option value="Server3">Server3</Option>
        </Select>
        <Card title="Info" style={{ marginTop: 24 }} bordered={false}>
          <DescriptionList>
            <Description term="OS Name">Mac OS X</Description>
            <Description term="Host Name">hanahmily</Description>
            <Description term="Process Id">21144</Description>
            <Description term="IPv4">192.168.1.4</Description>
          </DescriptionList>
        </Card>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg Response Time"
              total={`${this.avg(getServerResponseTimeTrend.trendList)} ms`}
            >
              <MiniArea
                animate={false}
                color="#975FE4"
                height={46}
                data={getServerResponseTimeTrend.trendList
                  .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg TPS"
              total={`${this.avg(getServerTPSTrend.trendList)} ms`}
            >
              <MiniBar
                animate={false}
                height={46}
                data={getServerTPSTrend.trendList
                  .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
            <Card
              title="CPU"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Line
                height={250}
                data={getCPUTrend.cost
                  .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
              />
            </Card>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
            <Card
              title="Heap"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Area
                height={250}
                data={getMemoryTrend.heap
                  .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'value' }))
                  .concat(getMemoryTrend.maxHeap
                  .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'limit ' })))}
              />
            </Card>
          </Col>
          <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
            <Card
              title="No-Heap"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Area
                height={250}
                data={getMemoryTrend.noheap
                  .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'value' }))
                  .concat(getMemoryTrend.maxNoheap
                  .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'limit ' })))}
              />
            </Card>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
            <Card
              title="GC"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Line
                height={250}
                data={getGCTrend.youngGC
                  .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
              />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }
}
