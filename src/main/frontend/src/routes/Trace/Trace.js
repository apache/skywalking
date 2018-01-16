import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Form, Input, Select, Button, Card, InputNumber } from 'antd';
import TraceTable from '../../components/TraceTable';
import styles from './Trace.less';

const { Option } = Select;
const FormItem = Form.Item;
const query = `query BasicTraces($condition: TraceQueryCondition){
  queryBasicTraces(condition: $condition)
}`;

@connect(state => ({
  trace: state.trace,
  duration: state.global.duration,
  loading: state.loading.models.trace,
}))
@Form.create()
export default class Trace extends PureComponent {
  state = {
    formValues: {},
  }
  handleSearch = (e) => {
    e.preventDefault();
    const { dispatch, form, duration: { input } } = this.props;

    form.validateFields((err, fieldsValue) => {
      if (err) return;

      const variables = {
        condition: {
          ...fieldsValue,
          queryDuration: input,
          paging: {
            pageNum: 1,
            pageSize: 10,
            needTotal: true,
          },
        },
      };

      this.setState({
        formValues: fieldsValue,
      });

      dispatch({
        type: 'trace/fetch',
        payload: { query, variables },
      });
    });
  }
  handleTableChange = (pagination) => {
    const { formValues } = this.state;
    const { dispatch, duration: { input } } = this.props;
    const variables = {
      condition: {
        ...formValues,
        queryDuration: input,
        paging: {
          pageNum: pagination.current,
          pageSize: pagination.pageSize,
          needTotal: true,
        },
      },
    };
    dispatch({
      type: 'trace/fetch',
      payload: { query, variables },
      pagination,
    });
  }
  renderForm() {
    const { getFieldDecorator } = this.props.form;
    return (
      <Form onSubmit={this.handleSearch} layout="inline">
        <Row gutter={{ md: 8, lg: 8, xl: 8 }}>
          <Col xl={4} sm={24}>
            <FormItem label="Application">
              {getFieldDecorator('application')(
                <Select placeholder="Select application" style={{ width: '100%' }}>
                  <Option value="all">All</Option>
                  <Option value="app2">app2</Option>
                </Select>
              )}
            </FormItem>
          </Col>
          <Col xl={4} sm={24}>
            <FormItem label="TraceId">
              {getFieldDecorator('traceId')(
                <Input placeholder="Input trace id" />
              )}
            </FormItem>
          </Col>
          <Col xl={6} sm={24}>
            <FormItem label="OperationName">
              {getFieldDecorator('operationName')(
                <Input placeholder="Input operation name" />
              )}
            </FormItem>
          </Col>
          <Col xl={6} sm={24}>
            <FormItem label="DurationRange">
              {getFieldDecorator('minTraceDuration')(
                <InputNumber style={{ width: '40%' }} />
              )}~
              {getFieldDecorator('maxTraceDuration')(
                <InputNumber style={{ width: '40%' }} />
              )}
            </FormItem>
          </Col>
          <Col xl={4} sm={24}>
            <span className={styles.submitButtons}>
              <Button type="primary" htmlType="submit">Search</Button>
            </span>
          </Col>
        </Row>
      </Form>
    );
  }
  render() {
    const { trace: { queryBasicTraces }, loading } = this.props;
    return (
      <Card bordered={false}>
        <div className={styles.tableList}>
          <div className={styles.tableListForm}>
            {this.renderForm()}
          </div>
          <TraceTable
            loading={loading}
            data={queryBasicTraces}
            onChange={this.handleTableChange}
          />
        </div>
      </Card>
    );
  }
}
