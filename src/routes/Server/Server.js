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
import { Row, Col, Card, Form } from 'antd';
import {
  ChartCard, MiniArea, MiniBar, Line, Area, StackBar,
} from '../../components/Charts';
import DescriptionList from '../../components/DescriptionList';
import { axis } from '../../utils/time';
import { avgTimeSeries } from '../../utils/utils';
import { Panel, Search } from '../../components/Page';

const { Description } = DescriptionList;
const { Item: FormItem } = Form;

@connect(state => ({
  server: state.server,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.server;
    return {
      serverId: Form.createFormField({
        value: { key: values.serverId ? values.serverId : '', label: labels.serverId ? labels.serverId : '' },
      }),
    };
  },
})
export default class Server extends PureComponent {
  handleSelect = (selected) => {
    this.props.dispatch({
      type: 'server/save',
      payload: {
        variables: {
          values: { serverId: selected.key },
          labels: { serverId: selected.label },
        },
        data: {
          serverInfo: selected,
        },
      },
    });
  }
  handleChange = (variables) => {
    this.props.dispatch({
      type: 'server/fetchData',
      payload: { variables },
    });
  }
  bytesToMB = list => list.map(_ => parseFloat((_ / (1024 ** 2)).toFixed(2)))
  render() {
    const { form, duration, server } = this.props;
    const { getFieldDecorator } = form;
    const { variables: { values }, data } = server;
    const { serverInfo, getServerResponseTimeTrend, getServerTPSTrend,
      getCPUTrend, getMemoryTrend, getGCTrend } = data;
    return (
      <div>
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('serverId')(
              <Search
                placeholder="Select a server"
                onSelect={this.handleSelect.bind(this)}
                url="/server/search"
                variables={{ duration: this.props.globalVariables.duration }}
                query={`
                  query SearchServer($keyword: String!, $duration: Duration!) {
                    searchServer(keyword: $keyword, duration: $duration) {
                      key: id
                      name
                      host
                      pid
                      ipv4
                      applicationCode
                    }
                  }
                `}
                transform={r => ({ ...r, label: `${r.pid}@${r.host}:${r.applicationCode}` })}
              />
            )}
          </FormItem>
        </Form>
        <Panel
          variables={values}
          globalVariables={this.props.globalVariables}
          onChange={this.handleChange}
        >
          <Card title="Info" style={{ marginTop: 24 }} bordered={false}>
            <DescriptionList>
              <Description term="Application Code">{serverInfo.applicationCode}</Description>
              <Description term="Host Name">{serverInfo.host}</Description>
              <Description term="IPv4">{serverInfo.ipv4 ? serverInfo.ipv4.join() : ''}</Description>
              <Description term="Process Id">{serverInfo.pid}</Description>
              <Description term="OS">{serverInfo.name}</Description>
            </DescriptionList>
          </Card>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Response Time"
                total={`${avgTimeSeries(getServerResponseTimeTrend.trendList)} ms`}
                contentHeight={46}
              >
                {getServerResponseTimeTrend.trendList.length > 0 ? (
                  <MiniArea
                    animate={false}
                    color="#975FE4"
                    data={axis(duration, getServerResponseTimeTrend.trendList)}
                  />
                ) : (<span style={{ display: 'none' }} />)}
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Throughput"
                total={`${avgTimeSeries(getServerTPSTrend.trendList)}`}
              >
                <MiniBar
                  animate={false}
                  height={46}
                  data={axis(duration, getServerTPSTrend.trendList)}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
              <ChartCard
                title="CPU %"
                contentHeight={150}
              >
                <Line
                  data={axis(duration, getCPUTrend.cost)}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="Heap MB"
                contentHeight={150}
              >
                <Area
                  data={axis(duration, this.bytesToMB(getMemoryTrend.heap), ({ x, y }) => ({ x, y, type: 'value' }))
                    .concat(axis(duration, this.bytesToMB(getMemoryTrend.maxHeap), ({ x, y }) => ({ x, y, type: 'free' })))}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="Non-Heap MB"
                contentHeight={150}
              >
                <Area
                  data={axis(duration, this.bytesToMB(getMemoryTrend.noheap), ({ x, y }) => ({ x, y, type: 'value' }))
                  .concat(axis(duration, this.bytesToMB(getMemoryTrend.maxNoheap), ({ x, y }) => ({ x, y, type: 'free' })))}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
              <ChartCard
                title="GC ms"
                contentHeight={150}
              >
                <StackBar
                  data={axis(duration, getGCTrend.oldGC, ({ x, y }) => ({ x, y, type: 'oldGC' }))
                  .concat(axis(duration, getGCTrend.youngGC, ({ x, y }) => ({ x, y, type: 'youngGC' })))}
                />
              </ChartCard>
            </Col>
          </Row>
        </Panel>
      </div>
    );
  }
}
