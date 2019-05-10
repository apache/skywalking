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
import { Button, Icon, Row, Col } from 'antd';
import TraceSearch from './TraceSearch';
import TraceTimeline from './TraceTimeline';

@connect(state => ({
  zone: state.global.zone,
  trace: state.trace,
  loading: state.loading.models.trace,
}))
export default class Trace extends PureComponent {
  handleGoBack = () => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'trace/hideTimeline',
    });
  }

  render() {
    const { trace: { data: { showTimeline } } } = this.props;
    const {...propsData} = this.props;
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
            <TraceSearch
              trace={propsData.trace}
              duration={propsData.duration}
              loading={propsData.loading}
              globalVariables={propsData.globalVariables}
              dispatch={propsData.dispatch}
              zone={propsData.zone}
            />
          </Col>
          <Col span={showTimeline ? 24 : 0}>
            {showTimeline ? (
              <TraceTimeline
                trace={propsData.trace}
                duration={propsData.duration}
                loading={propsData.loading}
                globalVariables={propsData.globalVariables}
                dispatch={propsData.dispatch}
              />
            ) : null}
          </Col>
        </Row>
      </div>
    );
  }
}
