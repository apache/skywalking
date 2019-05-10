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

import React, { Component } from 'react';
import { connect } from 'dva';
import { Row, Select, Form, Col, Button, Icon, Card } from 'antd';
import { Panel } from 'components/Page';
import { DatabaseChartArea, DatabaseChartBar, DatabaseChartLine } from 'components/Database';
import TraceList from '../../components/Trace/TraceListDB';
import TraceTimeline from '../Trace/TraceTimeline';
import { avgTS } from '../../utils/utils';
import { axisY, axisMY } from '../../utils/time';

const { Option } = Select;
const { Item: FormItem } = Form;

@connect(state => ({
  database: state.database,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.database;
    return {
      databaseId: Form.createFormField({
        value: { key: values.databaseId ? values.databaseId : '', label: labels.databaseId ? labels.databaseId : '' },
      }),
    };
  },
})
export default class Database extends Component {

  componentDidMount() {
    const propsData = this.props;
    propsData.dispatch({
      type: 'database/initOptions',
      payload: { variables: propsData.globalVariables },
    });
  }
  
  componentWillUpdate(nextProps) {
    const propsData = this.props;
    if (nextProps.globalVariables.duration === propsData.globalVariables.duration) {
      return;
    }
    propsData.dispatch({
      type: 'database/initOptions',
      payload: { variables: nextProps.globalVariables },
    });
  }

  handleSelect = (selected) => {
    const propsData = this.props;
    propsData.dispatch({
      type: 'database/saveVariables',
      payload: {
        values: { databaseId: selected.key },
        labels: { databaseId: selected.label },
      },
    });
  }

  handleSelectTopN = (selected) => {
    const {...propsData} = this.props;
    this.topNum = selected;
    propsData.dispatch({
      type: 'database/fetchTraces',
      payload: { variables: {
        condition:{
          serviceId:this.databaseId, 
          metricName:"top_n_database_statement",
          topN:selected, 
          order:'DES', 
          duration:propsData.globalVariables.duration,
        },
      }},
    });
  }

  handleChange = (variables) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'database/fetchData',
      payload: { variables, reducer: 'saveDatabase' },
    });
    this.databaseId = variables.databaseId;
    this.handleGetTraces(variables.databaseId);
  }

  handleGoBack = () => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'database/hideTimeline',
    });
  }

  handleShowTrace = (traceId) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'database/fetchSpans',
      payload: { variables: { traceId } },
    });
  }

  handleGetTraces = (databaseId) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'database/fetchTraces',
      payload: { variables: {
        condition:{
          serviceId:databaseId, 
          metricName:"top_n_database_statement",
          topN:this.topNum || 20, 
          order:'DES', 
          duration:propsData.globalVariables.duration,
        },
      }},
    });
  }

  render() {
    const propsData = this.props;
    const { duration } = this.props;
    const { getFieldDecorator } = propsData.form;
    const { variables: { values, options }, data } = propsData.database;
    const { showTimeline, queryTrace, currentTraceId } = data;
    return (
      <div>
        {showTimeline ? (
          <Row type="flex" justify="start">
            <Col style={{ marginBottom: 24 }}>
              <Button ghost type="primary" size="small" onClick={() => { this.handleGoBack(); }}>
                <Icon type="left" />Go back
              </Button>
            </Col>
          </Row>
      ) : null}
        <Row type="flex" justify="start">
          <Col span={showTimeline ? 0 : 24}>
            <Form layout="inline">
              <FormItem style={{ width: '100%' }}>
                {getFieldDecorator('databaseId')(
                  <Select
                    showSearch
                    style={{ minWidth: 350 }}
                    optionFilterProp="children"
                    placeholder="Select a database"
                    labelInValue
                    onSelect={this.handleSelect.bind(this)}
                  >
                    {options.databaseId && options.databaseId.map(db =>
                      db.key ?
                        <Option key={db.key} value={db.key}>{db.type}: {db.label}</Option>
                        :
                        null
                    )}
                  </Select>
                )}
              </FormItem>
            </Form>
            <Panel
              variables={values}
              globalVariables={propsData.globalVariables}
              onChange={this.handleChange}
            >
              <Row>
                <DatabaseChartArea
                  title="Avg Throughput"
                  total={`${avgTS(data.getThroughputTrend.values)} cpm`}
                  data={axisY(duration, data.getThroughputTrend.values)}
                />
                <DatabaseChartArea
                  title="Avg Response Time"
                  total={`${avgTS(data.getResponseTimeTrend.values)} ms`}
                  data={axisY(duration, data.getResponseTimeTrend.values)}
                />
                <DatabaseChartBar
                  title="Avg SLA"
                  total={`${(avgTS(data.getSLATrend.values) / 100).toFixed(2)} %`}
                  data={axisY(duration, data.getSLATrend.values, ({ x, y }) => ({ x, y: y / 100 }))}
                />
              </Row>
              <DatabaseChartLine
                title="Response Time"
                data={axisMY(propsData.duration, [{ title: 'p99', value: data.getP99}, { title: 'p95', value: data.getP95},
                { title: 'p90', value: data.getP90}, { title: 'p75', value: data.getP75}, { title: 'p50', value: data.getP50}])}
              />
              <Row gutter={8}>
                <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 8 }}>
                  <Card>
                    <span>Top </span>
                    <Select
                      style={{ minWidth: 70 }}
                      defaultValue={20}
                      onSelect={this.handleSelectTopN.bind(this)}
                    >
                      <Option value={20}>20</Option>
                      <Option value={50}>50</Option>
                      <Option value={100}>100</Option>
                    </Select>
                    <span> Slow SQL</span>
                    <TraceList
                      data={data.getTopNRecords}
                      onClickTraceTag={this.handleShowTrace}
                      loading={propsData.loading}
                    />
                  </Card>
                </Col>
              </Row>
            </Panel>
          </Col>
          <Col span={showTimeline ? 24 : 0}>
            {showTimeline ? (
              <TraceTimeline
                trace={{ data: { queryTrace, currentTraceId } }}
              />
            ) : null}
          </Col>
        </Row>
      </div>
    );
  }
}
