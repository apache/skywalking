import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Select, Card, Table } from 'antd';

@connect(state => ({
  application: state.application,
}))
export default class Dashboard extends PureComponent {
  render() {
    function handleChange(value) {
      console.log(`selected ${value}`);
    }
    function handleBlur() {
      console.log('blur');
    }

    function handleFocus() {
      console.log('focus');
    }
    const tableColumns = [{
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    }, {
      title: 'Duration',
      dataIndex: 'duration',
      key: 'duration',
    }];
    const { Option } = Select;
    const slowServiceData = [{
      key: '1',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '1',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '1',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '1',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '1',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
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

    const applicationThroughputData = [{
      key: '1',
      name: 'Server1',
      tps: '500',
    }, {
      key: '1',
      name: 'Server1',
      tps: '500',
    }, {
      key: '1',
      name: 'Server1',
      tps: '500',
    }, {
      key: '1',
      name: 'Server1',
      tps: '500',
    }, {
      key: '1',
      name: 'Server1',
      tps: '500',
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
          onChange={handleChange}
          onFocus={handleFocus}
          onBlur={handleBlur}
        >
          <Option value="App1">App1</Option>
          <Option value="App2">App2</Option>
          <Option value="App3">App3</Option>
        </Select>
        <Card
          bordered={false}
          bodyStyle={{ padding: 0, marginTop: 24 }}
        >
          <div Style="height: 400px">Application and externel resources(Db, Cache or MQ) Topoloy</div>
        </Card>
        <Row gutter={24}>
          <Col {...middleColResponsiveProps}>
            <Card
              title="Slow Service"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Table
                columns={tableColumns}
                dataSource={slowServiceData}
                pagination={{
                  style: { marginBottom: 0 },
                  pageSize: 5,
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
                columns={applicationThroughputColumns}
                dataSource={applicationThroughputData}
                pagination={{
                  style: { marginBottom: 0 },
                  pageSize: 5,
                }}
              />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }
}
