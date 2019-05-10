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
import { Row, Col, Card, Icon, Radio, Avatar, Select, Input, Popover, Tag } from 'antd';
import { ChartCard } from '../../components/Charts';
import { AppTopology } from '../../components/Topology';
import { Panel } from '../../components/Page';
import ApplicationLitePanel from '../../components/ApplicationLitePanel';
import DescriptionList from '../../components/DescriptionList';
import { redirect } from '../../utils/utils';

const { Description } = DescriptionList;
const { Option } = Select;

const colResponsiveProps = {
  xs: 24,
  sm: 24,
  md: 24,
  lg: 12,
  xl: 12,
  style: { marginTop: 8 },
};

const layouts = [
  {
    name: 'dagre',
    icon: 'img/icon/dagre.png',
    rankDir: 'LR',
    minLen: 4,
    animate: true,
  },
  {
    name: 'concentric',
    icon: 'img/icon/concentric.png',
    minNodeSpacing: 10,
    animate: true,
  },
  {
    name: 'cose-bilkent',
    icon: 'img/icon/cose.png',
    idealEdgeLength: 200,
    edgeElasticity: 0.1,
    randomize: false,
  },
];

const layoutButtonStyle = { height: '90%', verticalAlign: 'middle', paddingBottom: 2 };

@connect(state => ({
  topology: state.topology,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
export default class Topology extends PureComponent {
  static defaultProps = {
    graphHeight: 600,
  };

  findValue = (id, values) => {
    const v = values.find(_ => _.id === id);
    if (v) {
      return v.value;
    }
    return null;
  }

  handleChange = (variables) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'topology/fetchData',
      payload: { variables },
    });
  }

  handleLayoutChange = ({ target: { value } }) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'topology/saveData',
      payload: { layout: value },
    });
  }

  handleLoadMetrics = (ids, idsS, idsC) => {
    const { dispatch, globalVariables: { duration } } = this.props;
    dispatch({
      type: 'topology/fetchMetrics',
      payload: { variables: {
        duration,
        ids,
        idsS,
        idsC,
      }},
    });
  }

  handleSelectedApplication = (appInfo) => {
    const { dispatch, topology: { data: { metrics: { sla, nodeCpm, nodeLatency } } } } = this.props;
    if (appInfo) {
      dispatch({
        type: 'topology/saveData',
        payload: { appInfo: { ...appInfo,
          sla: this.findValue(appInfo.id, sla.values),
          cpm: this.findValue(appInfo.id, nodeCpm.values),
          avgResponseTime: this.findValue(appInfo.id, nodeLatency.values),
        } },
      });
    } else {
      dispatch({
        type: 'topology/saveData',
        payload: { appInfo: null },
      });
    }
  }

  handleChangeLatencyStyle = (e) => {
    const { value } = e.target;
    const vArray = value.split(',');
    if (vArray.length !== 2) {
      return;
    }
    const latencyRange = vArray.map(_ => parseInt(_.trim(), 10)).filter(_ => !isNaN(_));
    if(latencyRange[1] < 0) {
      latencyRange[1] = 0;
    }
    if(latencyRange[0] > latencyRange[1]) {
      const temp = latencyRange[1];
      latencyRange[0] = temp;
    }
    if (latencyRange.length !== 2) {
      return;
    }
    const { dispatch } = this.props;
    dispatch({
      type: 'topology/setLatencyStyleRange',
      payload: { latencyRange },
    });
  }

  handleFilterApplication = (aa) => {
    const { dispatch } = this.props;
    dispatch({
      type: 'topology/filterApplication',
      payload: { aa },
    });
  }

  renderActions = () => {
    const {...propsData} = this.props;
    const { data: { appInfo } } = propsData.topology;
    return [
      <Icon type="appstore" onClick={() => redirect(propsData.history, '/monitor/service', { key: appInfo.id, label: appInfo.name })} />,
      <Icon
        type="exception"
        onClick={() => redirect(propsData.history, '/trace',
        { values: {
            serviceId: appInfo.id,
            duration: { ...propsData.duration, input: propsData.globalVariables.duration },
          },
          labels: { applicationId: appInfo.name },
        })}
      />,
      appInfo.isAlarm ? <Icon type="bell" onClick={() => redirect(propsData.history, '/monitor/alarm')} /> : null,
    ];
  }

  renderNodeType = (topologData) => {
    const typeMap = new Map();
    topologData.nodes.forEach((_) => {
      if (typeMap.has(_.type)) {
        typeMap.set(_.type, typeMap.get(_.type) + 1);
      } else {
        typeMap.set(_.type, 1);
      }
    });
    const result = [];
    typeMap.forEach((v, k) => result.push(<Description term={k}>{v}</Description>));
    return result;
  }

  render() {
    const {...propsData} = this.props;
    const { data, variables: { appRegExps, appFilters = [], latencyRange } } = propsData.topology;
    const { metrics, layout = 0 } = data;
    const { getGlobalTopology: topologData } = data;
    const content = (
      <div>
        <p><Tag color="#40a9ff">Less than {latencyRange[0]} ms </Tag></p>
        <p><Tag color="#d4b106">Between {latencyRange[0]} ms and {latencyRange[1]} ms</Tag></p>
        <p><Tag color="#cf1322">More than {latencyRange[1]} ms</Tag></p>
      </div>
    );
    return (
      <Panel globalVariables={propsData.globalVariables} onChange={this.handleChange}>
        <Row gutter={8}>
          <Col {...{ ...colResponsiveProps, xl: 18, lg: 16 }}>
            <ChartCard
              title="Topology Map"
              avatar={<Avatar icon="fork" style={{ color: '#1890ff', backgroundColor: '#ffffff' }} />}
              action={(
                <Radio.Group value={layout} onChange={this.handleLayoutChange} size="normal">
                  {layouts.map((_, i) => (
                    <Radio.Button value={i} key={_.name}>
                      <img src={_.icon} alt={_.name} style={layoutButtonStyle} />
                    </Radio.Button>))}
                </Radio.Group>
              )}
            >
              {topologData.nodes.length > 0 ? (
                <AppTopology
                  height={propsData.graphHeight}
                  elements={topologData}
                  metrics={metrics}
                  onSelectedApplication={this.handleSelectedApplication}
                  onLoadMetircs={this.handleLoadMetrics}
                  layout={layouts[layout]}
                  latencyRange={latencyRange}
                  appRegExps={appRegExps}
                />
              ) : null}
            </ChartCard>
          </Col>
          <Col {...{ ...colResponsiveProps, xl: 6, lg: 8 }}>
            {data.appInfo ? (
              <Card
                title={data.appInfo.name}
                bodyStyle={{ height: 568 }}
                actions={this.renderActions()}
              >
                <ApplicationLitePanel appInfo={data.appInfo} />
              </Card>
            )
            : (
              <Card title="Overview" style={{ height: 672 }}>
                <Select
                  mode="tags"
                  style={{ width: '100%', marginBottom: 20 }}
                  placeholder="Filter application"
                  onChange={this.handleFilterApplication}
                  tokenSeparators={[',']}
                  value={appFilters}
                >
                  {data.getGlobalTopology.nodes.filter(_ => _.isReal)
                    .map(_ => <Option key={_.name}>{_.name}</Option>)}
                </Select>
                <Popover content={content} title="Info">
                  <h4>Latency coloring thresholds  <Icon type="info-circle-o" /></h4>
                </Popover>
                <Input style={{ width: '100%', marginBottom: 20 }} onChange={this.handleChangeLatencyStyle} value={latencyRange.join(',')} />
                <h4>Overview</h4>
                <DescriptionList layout="vertical">
                  <Description term="Total">{topologData.nodes.length}</Description>
                  {this.renderNodeType(topologData)}
                </DescriptionList>
              </Card>
            )}
          </Col>
        </Row>
      </Panel>
    );
  }
}
