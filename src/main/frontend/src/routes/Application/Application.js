import React, { Component } from 'react';
import { connect } from 'dva';
import { Row, Col, Select, Card, Table } from 'antd';
import { AppTopology } from '../../components/Topology';

const { Option } = Select;

@connect(state => ({
  application: state.application,
  duration: state.global.duration,
}))
export default class Application extends Component {
  shouldComponentUpdate(nextProps) {
    if (this.props.duration !== nextProps.duration) {
      this.props.dispatch({
        type: 'application/fetch',
        payload: {},
      });
    }
    return this.props.application !== nextProps.application;
  }
  handleChange(appId) {
    this.props.dispatch({
      type: 'application/fetch',
      payload: { applicationId: appId },
    });
  }
  render() {
    const tableColumns = [{
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    }, {
      title: 'Duration',
      dataIndex: 'avgResponseTime',
      key: 'avgResponseTime',
    }];

    const applicationThroughputColumns = [{
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    }, {
      title: 'Tps',
      dataIndex: 'tps',
      key: 'tps',
    }];

    const middleColResponsiveProps = {
      xs: 24,
      sm: 24,
      md: 12,
      lg: 12,
      xl: 12,
      style: { marginBottom: 24, marginTop: 24 },
    };
    return (
      <div>
        <Select
          showSearch
          style={{ width: 200 }}
          placeholder="Select a application"
          optionFilterProp="children"
          onChange={this.handleChange.bind(this)}
        >
          <Option value="App1">App1</Option>
          <Option value="App2">App2</Option>
          <Option value="App3">App3</Option>
        </Select>
        <Card
          bordered={false}
          bodyStyle={{ padding: 0, marginTop: 24 }}
        >
          <AppTopology elements={this.props.application.getApplicationTopology} layout={{ name: 'concentric', minNodeSpacing: 200 }} />
        </Card>
        <Row gutter={24}>
          <Col {...middleColResponsiveProps}>
            <Card
              title="Slow Service"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Table
                size="small"
                columns={tableColumns}
                dataSource={this.props.application.getSlowService}
                pagination={{
                  style: { marginBottom: 0 },
                  pageSize: 10,
                }}
              />
            </Card>
          </Col>
          <Col {...middleColResponsiveProps}>
            <Card
              title="Servers Throughput"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Table
                size="small"
                columns={applicationThroughputColumns}
                dataSource={this.props.application.getServerThroughput}
                pagination={{
                  style: { marginBottom: 0 },
                  pageSize: 10,
                }}
              />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }
}
