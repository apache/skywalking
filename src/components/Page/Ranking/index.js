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
import { List, Avatar } from 'antd';

export default class Ranking extends PureComponent {
  render() {
    let index = 0;
    const { data, title, content, unit } = this.props;
    return (
      <List
        size="small"
        itemLayout="horizontal"
        dataSource={data}
        renderItem={item => (
          <List.Item>
            <List.Item.Meta
              avatar={
                <Avatar
                  style={{ color: '#ff3333', backgroundColor: '#ffb84d' }}
                >
                  {(() => { index += 1; return index; })()}
                </Avatar>}
              title={item[title]}
              description={`${item[content]} ${unit}`}
            />
          </List.Item>
        )}
      />);
  }
}
