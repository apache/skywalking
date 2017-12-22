import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Select, Card, Tooltip, Icon, Table } from 'antd';
import moment from 'moment';
import {
  ChartCard, MiniArea, MiniBar, Line,
} from '../../components/Charts';
import DescriptionList from '../../components/DescriptionList';

const { Description } = DescriptionList;

@connect(state => ({
  service: state.service,
}))
export default class Dashboard extends PureComponent {
  render() {
    const visitData = [];
    const beginDay = new Date().getTime();

    const fakeY = [7, 5, 4, 2, 4, 7, 5, 6, 5, 9, 6, 3, 1, 5, 3, 6, 5];
    for (let i = 0; i < fakeY.length; i += 1) {
      visitData.push({
        x: moment(new Date(beginDay + (1000 * 60 * 60 * 24 * i))).format('YYYY-MM-DD'),
        y: fakeY[i],
      });
    }
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
      title: 'Time',
      dataIndex: 'time',
      key: 'time',
    }, {
      title: 'Entry',
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
    return (
      <div>
        <Select
          showSearch
          style={{ width: 200 }}
          placeholder="Select a server"
          optionFilterProp="children"
          onChange={handleChange}
          onFocus={handleFocus}
          onBlur={handleBlur}
        >
          <Option value="Server1">Server1</Option>
          <Option value="Server1">Server1</Option>
          <Option value="Server1">Server1</Option>
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
              action={<Tooltip title="Tip"><Icon type="info-circle-o" /></Tooltip>}
              total="300 ms"
            >
              <MiniArea
                color="#975FE4"
                height={46}
                data={visitData}
              />
            </ChartCard>
          </Col>
          <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg TPS"
              action={<Tooltip title="Tip"><Icon type="info-circle-o" /></Tooltip>}
              total="500"
            >
              <MiniBar
                height={46}
                data={visitData}
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
                data={visitData}
              />
            </Card>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
            <Card
              title="MEMORY"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Line
                height={250}
                data={visitData}
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
                data={visitData}
              />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }
}
