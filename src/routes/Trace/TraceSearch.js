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
import { Form, Input, Select, Button, Card, InputNumber, Row, Col, Pagination, DatePicker } from 'antd';
import { Chart, Geom, Axis, Tooltip, Legend } from 'bizcharts';
import { DataSet } from '@antv/data-set';
import moment from 'moment';
import TraceList from '../../components/Trace/TraceList';
import { generateDuration } from '../../utils/time';
import styles from './Trace.less';

const { Option } = Select;
const { RangePicker } = DatePicker;
const FormItem = Form.Item;
const initPaging = {
  pageNum: 1,
  pageSize: 20,
  needTotal: true,
};

@Form.create({
  mapPropsToFields(props) {
    const { variables: { values } } = props.trace;
    const result = {};
    Object.keys(values).filter(_ => _ !== 'range-time-picker').forEach((_) => {
      result[_] = Form.createFormField({
        value: values[_],
      });
    });
    const { duration } = values;
    if (duration) {
      result['range-time-picker'] = Form.createFormField({
        value: [duration.raw.start, duration.raw.end],
      });
    }
    return result;
  },
})
export default class Trace extends PureComponent {
  componentDidMount() {
    const { trace: { variables: { values } } } = this.props;
    const { duration } = values;
    this.props.dispatch({
      type: 'trace/initOptions',
      payload: { variables: { duration: duration.input } },
    });
    const condition = { ...values };
    condition.queryDuration = values.duration.input;
    delete condition.duration;
    this.fetchData(condition, initPaging);
  }
  getDefaultDuration = () => {
    return generateDuration({
      from() {
        return moment().subtract(15, 'minutes');
      },
      to() {
        return moment();
      },
    });
  }
  handleSearch = (e) => {
    if (e) {
      e.preventDefault();
    }
    const { form, dispatch } = this.props;

    form.validateFields((err, fieldsValue) => {
      if (err) return;
      const condition = { ...fieldsValue };
      delete condition['range-time-picker'];
      const rangeTime = fieldsValue['range-time-picker'];
      const duration = generateDuration({ from: () => rangeTime[0], to: () => rangeTime[1] });
      dispatch({
        type: 'trace/saveVariables',
        payload: {
          values: {
            ...condition,
            duration,
            paging: initPaging,
          },
        },
      });
      this.fetchData({ ...condition, queryDuration: duration.input });
    });
  }
  fetchData = (queryCondition, paging = initPaging) => {
    this.props.dispatch({
      type: 'trace/fetchData',
      payload: {
        variables: {
          condition: {
            ...queryCondition,
            paging,
          },
        },
      },
    });
  }
  handleTableChange = (pagination) => {
    const { dispatch, trace: { variables: { values } } } = this.props;
    const condition = {
      ...values,
      paging: {
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        needTotal: true,
      },
    };
    dispatch({
      type: 'trace/saveVariables',
      payload: {
        values: {
          ...condition,
        },
      },
    });
    delete condition.duration;
    this.fetchData({ ...condition, queryDuration: values.duration.input }, condition.paging);
  }
  handleShowTrace = (traceId) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'trace/fetchSpans',
      payload: { variables: { traceId } },
    });
  }
  renderPointChart = (traces) => {
    if (!traces) {
      return null;
    }
    const ds = new DataSet();
    const dv = ds.createView().source(traces);
    dv.transform({
      type: 'map',
      callback(row) {
        return {
          ...row,
          state: row.isError ? 'error' : 'success',
          startTime: moment(parseInt(row.start, 10)).format('YYYY-MM-DD HH:mm:ss.SSS'),
        };
      },
    });
    return (
      <Chart
        data={dv}
        height={432}
        forceFit
        scale={{
          startTime: {
            tickCount: 4,
          },
        }}
      >
        <Tooltip
          showTitle={false}
          crosshairs={{ type: 'cross' }}
          itemTpl='<li data-index={index} style="margin-bottom:4px;"><span style="background-color:{color};" class="g2-tooltip-marker"></span>{name}<br/>{value}</li>'
        />
        <Axis name="startTime" />
        <Axis
          name="duration"
          label={{
            formatter: (text) => {
              if (parseInt(text, 10) >= 1000) {
                return `${parseInt(text, 10) / 1000} s`;
              }
              return `${text} ms`;
            },
          }}
        />
        <Legend />
        <Geom
          type="point"
          position="startTime*duration"
          color={['state', state => (state === 'error' ? 'red' : '#1890ff')]}
          opacity={0.65}
          shape="circle"
          size={4}
          tooltip={['operationName*startTime*duration', (operationName, startTime, duration) => {
            return {
              name: operationName,
              value: `
                ${startTime}
                ${duration}ms
              `,
            };
          }]}
        />
      </Chart>);
  }
  renderForm() {
    const { getFieldDecorator } = this.props.form;
    const { trace: { variables: { options } } } = this.props;
    return (
      <Form onSubmit={this.handleSearch} layout="vertical">
        <FormItem label="Time Range">
          {getFieldDecorator('range-time-picker', {
            rules: [{
              required: true,
              message: 'Please select the correct date',
            }],
          })(
            <RangePicker
              showTime
              disabledDate={current => current && current.valueOf() >= Date.now()}
              format="YYYY-MM-DD HH:mm"
              style={{ width: '100%' }}
            />
          )}
        </FormItem>
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
        <FormItem label="Trace State">
          {getFieldDecorator('traceState')(
            <Select placeholder="All" style={{ width: '100%' }}>
              <Option key="success" value="SUCCESS">Success</Option>
              <Option key="error" value="ERROR">Error</Option>
              <Option key="all" value="ALL">All</Option>
            </Select>
          )}
        </FormItem>
        <FormItem label="Order By">
          {getFieldDecorator('queryOrder')(
            <Select placeholder="Start Time" style={{ width: '100%' }}>
              <Option key="BY_START_TIME" value="BY_START_TIME">Start Time</Option>
              <Option key="BY_DURATION" value="BY_DURATION">Duration</Option>
            </Select>
          )}
        </FormItem>
        <FormItem label="OperationName">
          {getFieldDecorator('operationName')(
            <Input placeholder="eg Kafka/Trace-topic-1/Consumer" />
          )}
        </FormItem>
        <FormItem label="TraceId">
          {getFieldDecorator('traceId')(
            <Input placeholder="eg 3.84.15204769998380001" />
          )}
        </FormItem>
        <Row>
          <Col xs={24} sm={24} md={24} lg={12} xl={12}>
            <FormItem label="Min Duration">
              {getFieldDecorator('minTraceDuration')(
                <InputNumber placeholder="eg 100" />
              )}
            </FormItem>
          </Col>
          <Col xs={24} sm={24} md={24} lg={12} xl={12}>
            <FormItem label="Max Duration">
              {getFieldDecorator('maxTraceDuration')(
                <InputNumber placeholder="eg 5000" />
              )}
            </FormItem>
          </Col>
        </Row>
        <FormItem>
          <Button type="primary" htmlType="submit">Search</Button>
        </FormItem>
      </Form>
    );
  }
  renderPage = (values, total) => {
    if (total < 1) {
      return null;
    }
    let currentPageNum = 1;
    let currentPageSize = 20;
    if (values.paging) {
      const { paging: { pageNum, pageSize } } = values;
      currentPageNum = pageNum;
      currentPageSize = pageSize;
    }
    return (
      <Row type="flex" justify="end">
        <Col>
          <Pagination
            size="small"
            current={currentPageNum}
            pageSize={currentPageSize}
            total={total}
            defaultPageSize={20}
            showSizeChanger
            pageSizeOptions={['20', '50', '100', '200']}
            onChange={(page, pageSize) => {
              this.handleTableChange({ current: page, pageSize });
            }}
            onShowSizeChange={(current, size) => {
              this.handleTableChange({ current: 1, pageSize: size });
            }}
          />
        </Col>
      </Row>);
  }
  render() {
    const { trace: { variables: { values }, data: { queryBasicTraces } }, loading } = this.props;
    return (
      <Card bordered={false}>
        <div className={styles.tableList}>
          <Row>
            <Col xs={24} sm={24} md={24} lg={8} xl={8}>
              {this.renderForm()}
            </Col>
            <Col xs={24} sm={24} md={24} lg={16} xl={16}>
              {this.renderPointChart(queryBasicTraces.traces)}
            </Col>
          </Row>
          {this.renderPage(values, queryBasicTraces.total)}
          <TraceList
            loading={loading}
            data={queryBasicTraces.traces}
            onClickTraceTag={this.handleShowTrace}
          />
          {this.renderPage(values, queryBasicTraces.total)}
        </div>
      </Card>
    );
  }
}
