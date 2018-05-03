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
import { List, Row, Col, Tag } from 'antd';
import styles from './index.less';


class RankList extends PureComponent {
  renderLabel = (item) => {
    const { renderLabel } = this.props;
    if (!renderLabel) {
      return item.label;
    }
    return renderLabel(item);
  }
  renderValue = (item) => {
    const { renderValue } = this.props;
    if (!renderValue) {
      return item.value;
    }
    return renderValue(item);
  }
  renderTitle = (item, maxValue) => {
    const { onClick, color = '#87CEFA' } = this.props;
    return (
      <div className={styles.progressWrap}>
        {maxValue > 0 ? (
          <div
            className={styles.progress}
            style={{
              backgroundColor: color,
              width: `${(item.value * 100) / maxValue}%`,
              height: 25,
            }}
          />
        ) : null}
        <div className={styles.mainInfo}>
          <span>{onClick ?
            <a onClick={() => onClick(item.key, item)}>{this.renderLabel(item)}</a>
              : this.renderLabel(item)}
          </span>
          <span className={styles.value}>{this.renderValue(item)}
          </span>
        </div>
      </div>);
  }
  renderBadges = (item) => {
    const { renderBadge } = this.props;
    return (
      <Row type="flex" justify="start" gutter={15}>
        {
          renderBadge(item).map((_) => {
            return (
              <Col key={_.key}>
                <span>{_.label}</span>
                {Array.isArray(_.value) ? _.value.map(
                  v => <Tag key={v} style={{ marginLeft: 5 }}>{v}</Tag>)
                  : <Tag style={{ marginLeft: 5 }}>{_.value}</Tag>
                }
              </Col>
            );
          })
        }
      </Row>
    );
  }
  render() {
    const { data, loading, renderBadge } = this.props;
    let maxValue = 0;
    const sortData = [...data];
    sortData.sort((a, b) => {
      if (a.value > b.value) {
        maxValue = a.value > maxValue ? a.value : maxValue;
      } else {
        maxValue = b.value > maxValue ? b.value : maxValue;
      }
      return b.value - a.value;
    });
    return (
      <List
        className={styles.rankList}
        itemLayout="horizontal"
        size="small"
        dataSource={sortData}
        loading={loading}
        renderItem={item => (
          <List.Item>
            <List.Item.Meta
              title={this.renderTitle(item, maxValue)}
              description={renderBadge ? this.renderBadges(item) : null}
            />
          </List.Item>
        )}
      />
    );
  }
}

export default RankList;
