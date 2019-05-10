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
import { Col } from 'antd';
import { ChartCard, MiniBar } from 'components/Charts';

export default class DatabaseChartBar extends Component {
  render() {
    const {title, total, data} = this.props;
    return (
      <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ padding: '0 4px',marginTop: 8 }}>
        <ChartCard
          title={title}
          total={total}
          contentHeight={46}
        >
          <MiniBar
            // animate={false}
            data={data}
          />
        </ChartCard>
      </Col>
    );
  }
}
