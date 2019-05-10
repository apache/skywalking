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
import { Row, Col, Card, Select, Icon } from 'antd';
import {
  ChartCard, MiniArea, MiniBar,
} from "../Charts";
import DescriptionList from "../DescriptionList";
import { axisY } from '../../utils/time';
import { avgTS, getAttributes, getServiceInstanceId } from '../../utils/utils';

const { Option } = Select;
const { Description } = DescriptionList;

export default class ServiceInstanceLitePanel extends PureComponent {
  bytesToMB = list => list.map(_ => parseFloat((_ / (1024 ** 2)).toFixed(2)))

  render() {
    const { serviceInstanceList, duration, data, onSelectServiceInstance, onMoreServiceInstance } = this.props;
    if (serviceInstanceList.length < 1) {
      return null;
    }
    const { serviceInstanceInfo, getServiceInstanceResponseTimeTrend, getServiceInstanceThroughputTrend, getServiceInstanceSLA } = data;
    if (!serviceInstanceInfo.key) {
      onSelectServiceInstance(serviceInstanceList[0].key, serviceInstanceList[0]);
    }
    const { attributes } = serviceInstanceInfo;
    return (
      <div>
        <Row gutter={0}>
          <Col span={24}>
            <Select
              size="small"
              value={serviceInstanceInfo.key}
              onChange={value => onSelectServiceInstance(value, serviceInstanceList.find(_ => _.key === value))}
              style={{ width: '100%' }}
            >
              {serviceInstanceList.map(_ => <Option key={_.key} value={_.key}>{getServiceInstanceId(_)}</Option>)}
            </Select>
          </Col>
          <Col span={24}>
            <Card bordered={false} bodyStyle={{ padding: 5 }}>
              <DescriptionList col={1} gutter={0} size="small">
                <Description term="Host">{getAttributes(attributes, 'host_name')}</Description>
                <Description term="OS">{getAttributes(attributes, 'os_name')}</Description>
              </DescriptionList>
            </Card>
          </Col>
          <Col span={24}>
            <ChartCard
              title={`Ins:${serviceInstanceInfo.name} Throughput`}
              total={`${avgTS(getServiceInstanceThroughputTrend.values)} cpm`}
              contentHeight={46}
              bordered={false}
              bodyStyle={{ padding: 5 }}
            >
              <MiniBar
                data={axisY(duration, getServiceInstanceThroughputTrend.values)}
                color="#975FE4"
              />
            </ChartCard>
          </Col>
          <Col span={24}>
            <ChartCard
              title={`Ins:${serviceInstanceInfo.name} Response Time`}
              total={`${avgTS(getServiceInstanceResponseTimeTrend.values)} ms`}
              contentHeight={46}
              bordered={false}
              bodyStyle={{ padding: 5 }}
            >
              {getServiceInstanceResponseTimeTrend.values.length > 0 ? (
                <MiniArea
                  animate={false}
                  color="#87cefa"
                  data={axisY(duration, getServiceInstanceResponseTimeTrend.values)}
                />
              ) : (<span style={{ display: 'none' }} />)}
            </ChartCard>
          </Col>
          <Col span={24}>
            <ChartCard
              title={`Ins:${serviceInstanceInfo.name} SLA`}
              total={`${(avgTS(getServiceInstanceSLA.values) / 100).toFixed(2)} %`}
              contentHeight={46}
              bordered={false}
              bodyStyle={{ padding: 5 }}
            >
              {getServiceInstanceSLA.values.length > 0 ? (
                <MiniBar
                  animate={false}
                  data={axisY(duration, getServiceInstanceSLA.values,
                    ({ x, y }) => ({ x, y: y / 100 }))}
                />
              ) : (<span style={{ display: 'none' }} />)}
            </ChartCard>
          </Col>
        </Row>
        {serviceInstanceInfo.key ? <a style={{ float: 'right' }} onClick={onMoreServiceInstance}> More Server Details<Icon type="ellipsis" /> </a> : null}
      </div>
    );
  }
}
