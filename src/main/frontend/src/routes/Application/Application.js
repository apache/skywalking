import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Select, Card, Table, Form } from 'antd';
import { AppTopology } from '../../components/Topology';
import { Panel } from '../../components/Page';

const { Option } = Select;
const { Item: FormItem } = Form;

@connect(state => ({
  application: state.application,
  duration: state.global.duration,
}))
@Form.create({
  mapPropsToFields(props) {
    return {
      applicationId: Form.createFormField({
        value: props.application.applicationId,
      }),
    };
  },
})
export default class Application extends PureComponent {
  handleDurationChange = (duration) => {
    this.props.dispatch({
      type: 'application/loadAllApp',
      payload: { duration },
    });
  }
  handleChange = (applicationId) => {
    this.props.dispatch({
      type: 'application/fetchItem',
      payload: {
        variables:
        {
          applicationId,
          duration: this.props.duration,
        },
        data:
        {
          applicationId,
        },
      },
    });
  }
  render() {
    const { getFieldDecorator } = this.props.form;
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
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('applicationId')(
              <Select
                showSearch
                style={{ width: 200 }}
                placeholder="Select a application"
                optionFilterProp="children"
                onSelect={this.handleChange.bind(this)}
              >
                {this.props.application.allApplication.map((app) => {
                    return (<Option value={app.key}>{app.name}</Option>);
                  })}
              </Select>
            )}
          </FormItem>
        </Form>
        <Panel duration={this.props.duration} onDurationChange={this.handleDurationChange}>
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
        </Panel>
      </div>
    );
  }
}
