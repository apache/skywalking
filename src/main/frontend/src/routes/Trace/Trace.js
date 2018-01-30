import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Form, Input, Select, Button, Card, InputNumber } from 'antd';
import TraceTable from '../../components/TraceTable';
import { Panel } from '../../components/Page';
import styles from './Trace.less';

const { Option } = Select;
const FormItem = Form.Item;


@connect(state => ({
  trace: state.trace,
  duration: state.global.duration,
  loading: state.loading.models.trace,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values } } = props.trace;
    const result = {};
    Object.keys(values).forEach((_) => {
      result[_] = Form.createFormField({
        value: values[_],
      });
    });
    return result;
  },
})
export default class Trace extends PureComponent {
  componentDidMount() {
    this.props.dispatch({
      type: 'trace/initOptions',
      payload: { variables: this.props.globalVariables },
    });
  }
  handleChange = (variables) => {
    const filteredVariables = { ...variables };
    filteredVariables.queryDuration = filteredVariables.duration;
    delete filteredVariables.duration;
    if (filteredVariables.applicationCodes && !Array.isArray(filteredVariables.applicationCodes)) {
      filteredVariables.applicationCodes = [filteredVariables.applicationCodes];
    }
    this.props.dispatch({
      type: 'trace/fetchData',
      payload: { variables: { condition: filteredVariables } },
    });
  }
  handleSearch = (e) => {
    if (e) {
      e.preventDefault();
    }
    const { dispatch, form, globalVariables: { duration } } = this.props;

    form.validateFields((err, fieldsValue) => {
      if (err) return;
      dispatch({
        type: 'trace/saveVariables',
        payload: {
          values: {
            ...fieldsValue,
            queryDuration: duration,
            paging: {
              pageNum: 1,
              pageSize: 10,
              needTotal: true,
            },
          },
        },
      });
    });
  }
  handleTableChange = (pagination) => {
    const { dispatch, globalVariables: { duration },
      trace: { variables: { values } } } = this.props;
    dispatch({
      type: 'trace/saveVariables',
      payload: {
        values: {
          ...values,
          queryDuration: duration,
          paging: {
            pageNum: pagination.current,
            pageSize: pagination.pageSize,
            needTotal: true,
          },
        },
      },
    });
  }
  handleTableExpand = (expanded, record) => {
    const { dispatch } = this.props;
    if (expanded && !record.spans) {
      dispatch({
        type: 'trace/fetchSpans',
        payload: { variables: { traceId: record.traceId } },
      });
    }
  }
  renderForm() {
    const { getFieldDecorator } = this.props.form;
    const { trace: { variables: { options } } } = this.props;
    return (
      <Form onSubmit={this.handleSearch} layout="inline">
        <Row gutter={{ md: 8, lg: 8, xl: 8 }}>
          <Col xl={4} sm={24}>
            <FormItem label="Application">
              {getFieldDecorator('applicationCodes')(
                <Select mode="multiple" placeholder="Select application" style={{ width: '100%' }}>
                  {options.applicationCodes && options.applicationCodes.map((app) => {
                      return (<Option value={app.key}>{app.label}</Option>);
                    })}
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
    const { trace: { variables: { values }, data: { queryBasicTraces } }, loading } = this.props;
    return (
      <Card bordered={false}>
        <div className={styles.tableList}>
          <div className={styles.tableListForm}>
            {this.renderForm()}
          </div>
          <Panel
            variables={values}
            globalVariables={this.props.globalVariables}
            onChange={this.handleChange}
          >
            <TraceTable
              loading={loading}
              data={queryBasicTraces.traces}
              pagination={{ ...values.paging, total: queryBasicTraces.total }}
              onChange={this.handleTableChange}
              onExpand={this.handleTableExpand}
            />
          </Panel>
        </div>
      </Card>
    );
  }
}
