/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


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
  componentWillUpdate(nextProps) {
    if (nextProps.globalVariables.duration === this.props.globalVariables.duration) {
      return;
    }
    this.props.dispatch({
      type: 'trace/initOptions',
      payload: { variables: nextProps.globalVariables },
    });
  }
  handleChange = (variables) => {
    const filteredVariables = { ...variables };
    filteredVariables.queryDuration = filteredVariables.duration;
    delete filteredVariables.duration;
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
  handleTableExpand = (key, traceId) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'trace/fetchSpans',
      payload: { variables: { traceId }, key },
    });
  }
  renderForm() {
    const { getFieldDecorator } = this.props.form;
    const { trace: { variables: { options } } } = this.props;
    return (
      <Form onSubmit={this.handleSearch} layout="inline">
        <Row gutter={{ md: 8, lg: 12, xl: 8 }}>
          <Col xl={4} lg={12} sm={24}>
            <FormItem label="Application">
              {getFieldDecorator('applicationId')(
                <Select placeholder="All application" style={{ width: '100%' }}>
                  {options.applicationId && options.applicationId.map((app) => {
                      return (
                        <Option key={app.key ? app.key : -1} value={app.key}>
                          {app.label}
                        </Option>);
                    })}
                </Select>
              )}
            </FormItem>
          </Col>
          <Col xl={4} lg={12} sm={24}>
            <FormItem label="TraceId">
              {getFieldDecorator('traceId')(
                <Input placeholder="Input trace id" />
              )}
            </FormItem>
          </Col>
          <Col xl={6} lg={12} sm={24}>
            <FormItem label="OperationName">
              {getFieldDecorator('operationName')(
                <Input placeholder="Input operation name" />
              )}
            </FormItem>
          </Col>
          <Col xl={6} lg={12} sm={24}>
            <FormItem label="DurationRange">
              {getFieldDecorator('minTraceDuration')(
                <InputNumber style={{ width: '40%' }} />
              )}~
              {getFieldDecorator('maxTraceDuration')(
                <InputNumber style={{ width: '40%' }} />
              )}
            </FormItem>
          </Col>
          <Col xl={4} lg={12} sm={24}>
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
