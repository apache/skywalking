import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Select, Card, Table, Form } from 'antd';
import { AppTopology } from '../../components/Topology';
import { Panel } from '../../components/Page';

const { Option } = Select;
const { Item: FormItem } = Form;

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

@connect(state => ({
  application: state.application,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.application;
    return {
      applicationId: Form.createFormField({
        value: { key: values.applicationId, label: labels.applicationId },
      }),
    };
  },
})
export default class Application extends PureComponent {
  componentDidMount() {
    this.props.dispatch({
      type: 'application/initOptions',
      payload: { variables: this.props.globalVariables },
    });
  }
  componentWillUpdate(nextProps) {
    if (nextProps.globalVariables.duration === this.props.globalVariables.duration) {
      return;
    }
    this.props.dispatch({
      type: 'application/initOptions',
      payload: { variables: nextProps.globalVariables },
    });
  }
  handleSelect = (selected) => {
    this.props.dispatch({
      type: 'application/saveVariables',
      payload: {
        values: { applicationId: selected.key },
        labels: { applicationId: selected.label },
      },
    });
  }
  handleChange = (variables) => {
    this.props.dispatch({
      type: 'application/fetchData',
      payload: { variables },
    });
  }
  render() {
    const { getFieldDecorator } = this.props.form;
    const { variables: { values, options }, data } = this.props.application;
    return (
      <div>
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('applicationId')(
              <Select
                showSearch
                style={{ width: 200 }}
                placeholder="Select a application"
                labelInValue
                onSelect={this.handleSelect.bind(this)}
              >
                {options.applicationId && options.applicationId.map((app) => {
                    return (<Option value={app.key}>{app.label}</Option>);
                  })}
              </Select>
            )}
          </FormItem>
        </Form>
        <Panel
          variables={values}
          globalVariables={this.props.globalVariables}
          onChange={this.handleChange}
        >
          <Card
            bordered={false}
            bodyStyle={{ padding: 0, marginTop: 24 }}
          >
            <AppTopology elements={data.getApplicationTopology} layout={{ name: 'concentric', minNodeSpacing: 200 }} />
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
                  dataSource={data.getSlowService}
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
                  dataSource={data.getServerThroughput}
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
