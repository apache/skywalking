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
import { Card, Badge, Row, Col, Tag } from 'antd';
import moment from 'moment';
import { formatDuration } from '../../utils/time';
import TraceStack from '../../components/TraceStack';

const timeFormat = 'YYYY-MM-DD HH:mm:ss';

export default class TraceTimeLine extends PureComponent {
  getTotalDuration = (spans) => {
    let minStartTime = 0;
    let maxEndTime = 0;
    spans.forEach((span) => {
      if (minStartTime < 1 || minStartTime > span.startTime) {
        minStartTime = span.startTime;
      }
      if (maxEndTime < span.endTime) {
        maxEndTime = span.endTime;
      }
    });
    return formatDuration(maxEndTime - minStartTime);
  }
  getStartTime = (spans) => {
    let minStartTime = 0;
    spans.forEach((span) => {
      if (minStartTime < 1 || minStartTime > span.startTime) {
        minStartTime = span.startTime;
      }
    });
    return moment(minStartTime).format(timeFormat);
  }

  renderTitle = (items) => {
    return (
      <Row type="flex" justify="start" gutter={15}>
        {
          items.map((_) => {
            return (
              <Col key={_.name}>
                <span>{_.name}</span>
                <Badge count={_.count} style={{ backgroundColor: '#1890FF', marginLeft: 5 }} />
              </Col>
            );
          })
        }
      </Row>
    );
  }

  render() {
    const { trace: { data: { queryTrace: { spans }, currentTraceId } } } = this.props;
    if (spans.length < 1) {
      return null;
    }
    return (
      <Card
        title={
          this.renderTitle([
            {
              name: 'Start Time',
              count: this.getStartTime(spans),
            },
            {
              name: 'Total Duration',
              count: this.getTotalDuration(spans),
            },
            {
              name: 'Spans',
              count: spans.length,
            },
          ])
        }
      >
        <Tag style={{ marginBottom: 20 }}>{currentTraceId}</Tag>
        <TraceStack spans={spans} />
      </Card>
    );
  }
}
