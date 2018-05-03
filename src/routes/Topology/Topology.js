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
import { Row, Col, Card, Icon, Radio, Avatar } from 'antd';
import { ChartCard } from '../../components/Charts';
import { AppTopology } from '../../components/Topology';
import { Panel } from '../../components/Page';
import ApplicationLitePanel from '../../components/ApplicationLitePanel';
import DescriptionList from '../../components/DescriptionList';
import { redirect } from '../../utils/utils';

const { Description } = DescriptionList;

const colResponsiveProps = {
  xs: 24,
  sm: 24,
  md: 24,
  lg: 12,
  xl: 12,
  style: { marginTop: 8 },
};

const layouts = {
  'cose-bilkent': {
    name: 'cose-bilkent',
    idealEdgeLength: 200,
    edgeElasticity: 0.1,
  },
  dagre: {
    name: 'dagre',
    rankDir: 'LR',
    minLen: 4,
    animate: true,
  },
  concentric: {
    name: 'concentric',
    minNodeSpacing: 10,
    animate: true,
  },
};

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
  handleChange = (variables) => {
    this.props.dispatch({
      type: 'topology/fetchData',
      payload: { variables },
    });
  }
  handleLayoutChange = ({ target: { value } }) => {
    this.props.dispatch({
      type: 'topology/saveData',
      payload: { layout: layouts[value] },
    });
  }
  handleSelectedApplication = (appInfo) => {
    if (appInfo) {
      this.props.dispatch({
        type: 'topology/saveData',
        payload: { appInfo },
      });
    } else {
      this.props.dispatch({
        type: 'topology/saveData',
        payload: { appInfo: null },
      });
    }
  }
  renderActions = () => {
    const { data: { appInfo } } = this.props.topology;
    return [
      <Icon type="appstore" onClick={() => redirect(this.props.history, '/monitor/application', { key: appInfo.id, label: appInfo.name })} />,
      <Icon type="exception" onClick={() => redirect(this.props.history, '/trace', { key: appInfo.id, label: appInfo.name })} />,
      appInfo.isAlarm ? <Icon type="bell" onClick={() => redirect(this.props.history, '/monitor/alarm')} /> : null,
    ];
  }
  render() {
    const { data } = this.props.topology;
    const { layout = layouts['cose-bilkent'] } = data;
    return (
      <Panel globalVariables={this.props.globalVariables} onChange={this.handleChange}>
        <Row gutter={8}>
          <Col {...{ ...colResponsiveProps, xl: 18, lg: 16 }}>
            <ChartCard
              title="Topology Map"
              avatar={<Avatar icon="fork" style={{ color: '#1890ff', backgroundColor: '#ffffff' }} />}
              action={(
                <Radio.Group value={layout.name} onChange={this.handleLayoutChange} size="normal">
                  <Radio.Button value="cose-bilkent"><img src="img/icon/cose.png" alt="cose" style={layoutButtonStyle} /></Radio.Button>
                  <Radio.Button value="dagre"><img src="img/icon/dagre.png" alt="dagre" style={layoutButtonStyle} /></Radio.Button>
                  <Radio.Button value="concentric"><img src="img/icon/concentric.png" alt="concentric" style={layoutButtonStyle} /></Radio.Button>
                </Radio.Group>
              )}
            >
              {data.getClusterTopology.nodes.length > 0 ? (
                <AppTopology
                  height={this.props.graphHeight}
                  elements={data.getClusterTopology}
                  onSelectedApplication={this.handleSelectedApplication}
                  layout={layout}
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
                <DescriptionList col={1} layout="vertical" >
                  <Description term="Total Application">{data.getClusterTopology.nodes.filter(_ => _.sla).length}</Description>
                  <Description term="Application Alarm">{data.getClusterTopology.nodes.filter(_ => _.isAlarm).length}</Description>
                </DescriptionList>
              </Card>
            )}
          </Col>
        </Row>
      </Panel>
    );
  }
}
