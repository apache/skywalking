import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Icon, Tooltip, Card, Table } from 'antd';
import moment from 'moment';
import {
  ChartCard, Pie, MiniArea, Field,
} from '../../components/Charts';

@connect(state => ({
  dashboard: state.dashboard,
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
    const databasePieData = [
      {
        x: 'MySQL',
        y: 10,
      },
      {
        x: 'Oracle',
        y: 7,
      },
      {
        x: 'SQLServer',
        y: 3,
      },
    ];
    const tableColumns = [{
      title: 'Time',
      dataIndex: 'time',
      key: 'time',
    }, {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    }, {
      title: 'Duration',
      dataIndex: 'duration',
      key: 'duration',
    }];

    const slowServiceData = [{
      key: '1',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '2',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '3',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '4',
      name: 'ServiceA',
      time: '2017/12/11 19:22:32',
      duration: '5000ms',
    }, {
      key: '5',
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
      name: 'App1',
      tps: '500',
    }, {
      key: '2',
      name: 'App1',
      tps: '500',
    }, {
      key: '3',
      name: 'App1',
      tps: '500',
    }, {
      key: '4',
      name: 'App1',
      tps: '500',
    }, {
      key: '5',
      name: 'App1',
      tps: '500',
    }];

    const topColResponsiveProps = {
      xs: 24,
      sm: 12,
      md: 12,
      lg: 6,
      xl: 6,
      style: { marginBottom: 24 },
    };
    const middleColResponsiveProps = {
      xs: 24,
      sm: 24,
      md: 24,
      lg: 8,
      xl: 8,
      style: { marginBottom: 24, marginTop: 24 },
    };
    return (
      <div>
        <Row gutter={24}>
          <Col {...topColResponsiveProps}>
            <ChartCard
              title="Total Application"
              avatar={<img style={{ width: 56, height: 56 }} src="app.svg" alt="app" />}
              action={<Tooltip title="Tip"><Icon type="info-circle-o" /></Tooltip>}
              total={25}
            />
          </Col>
          <Col {...topColResponsiveProps}>
            <ChartCard
              title="Total Service"
              avatar={<img style={{ width: 56, height: 56 }} src="service.svg" alt="service" />}
              action={<Tooltip title="Tip"><Icon type="info-circle-o" /></Tooltip>}
              total={525}
            />
          </Col>
          <Col {...topColResponsiveProps}>
            <ChartCard
              title="Total Database"
              avatar={<img style={{ width: 56, height: 56 }} src="database.svg" alt="database" />}
              action={<Tooltip title="Tip"><Icon type="info-circle-o" /></Tooltip>}
              total={18}
            />
          </Col>
          <Col {...topColResponsiveProps}>
            <ChartCard
              title="Total Cache"
              avatar={<img style={{ width: 56, height: 56 }} src="redis.svg" alt="redis" />}
              action={<Tooltip title="Tip"><Icon type="info-circle-o" /></Tooltip>}
              total={5}
            />
          </Col>
        </Row>
        <Card
          bordered={false}
          bodyStyle={{ padding: 0 }}
        >
          <div style={{ height: 480 }}>Topoloy</div>
        </Card>
        <Row gutter={24}>
          <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
            <ChartCard
              title="Avg Application Alert"
              avatar={<img style={{ width: 56, height: 56 }} src="alert.svg" alt="app" />}
              action={<Tooltip title="Tip"><Icon type="info-circle-o" /></Tooltip>}
              total="5%"
              footer={<div><Field label="Max" value="10%" /> <Field label="Min" value="2%" /></div>}
            >
              <MiniArea
                color="#D87093"
                borderColor="#B22222"
                line="true"
                height={96}
                data={visitData}
                yAxis={{
                  formatter(val) {
                      return `${val} %`;
                  },
                }}
              />
            </ChartCard>
          </Col>
        </Row>
        <Row gutter={24}>
          <Col {...middleColResponsiveProps}>
            <Card
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Pie
                hasLegend
                title="Database"
                subTitle="Total"
                total={databasePieData.reduce((pre, now) => now.y + pre, 0)}
                data={databasePieData}
                height={300}
                lineWidth={4}
              />
            </Card>
          </Col>
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
              title="Application Throughput"
              bordered={false}
              bodyStyle={{ padding: 0 }}
            >
              <Table
                columns={applicationThroughputColumns}
                dataSource={applicationThroughputData}
              />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }
}
