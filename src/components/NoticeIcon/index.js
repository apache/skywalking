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
import { Popover, Icon, Tabs, Badge, Spin } from 'antd';
import classNames from 'classnames';
import List from './NoticeList';
import styles from './index.less';

const { TabPane } = Tabs;

export default class NoticeIcon extends PureComponent {
  static defaultProps = {
    onItemClick: () => {},
    onPopupVisibleChange: () => {},
    onTabChange: () => {},
    onClear: () => {},
    loading: false,
    locale: {
      emptyText: '暂无数据',
      clear: '清空',
    },
    emptyImage: 'https://gw.alipayobjects.com/zos/rmsportal/wAhyIChODzsoKIOBHcBk.svg',
  };
  static Tab = TabPane;
  constructor(props) {
    super(props);
    this.state = {};
    if (props.children && props.children[0]) {
      this.state.tabType = props.children[0].props.title;
    }
  }
  onItemClick = (item, tabProps) => {
    const { onItemClick } = this.props;
    onItemClick(item, tabProps);
  }
  onTabChange = (tabType) => {
    this.setState({ tabType });
    this.props.onTabChange(tabType);
  }
  getNotificationBox() {
    const { children, loading, locale } = this.props;
    if (!children) {
      return null;
    }
    const panes = React.Children.map(children, (child) => {
      const title = child.props.list && child.props.list.length > 0
        ? `${child.props.title} (${child.props.list.length})` : child.props.title;
      return (
        <TabPane tab={title} key={child.props.title}>
          <List
            {...child.props}
            data={child.props.list}
            onClick={item => this.onItemClick(item, child.props)}
            onClear={() => this.props.onClear(child.props.title)}
            title={child.props.title}
            locale={locale}
          />
        </TabPane>
      );
    });
    return (
      <Spin spinning={loading} delay={0}>
        <Tabs className={styles.tabs} onChange={this.onTabChange}>
          {panes}
        </Tabs>
      </Spin>
    );
  }
  render() {
    const { className, count, popupAlign, onPopupVisibleChange } = this.props;
    const noticeButtonClass = classNames(className, styles.noticeButton);
    const notificationBox = this.getNotificationBox();
    const trigger = (
      <span className={noticeButtonClass}>
        <Badge count={count} className={styles.badge}>
          <Icon type="bell" className={styles.icon} />
        </Badge>
      </span>
    );
    if (!notificationBox) {
      return trigger;
    }
    const popoverProps = {};
    if ('popupVisible' in this.props) {
      popoverProps.visible = this.props.popupVisible;
    }
    return (
      <Popover
        placement="bottomRight"
        content={notificationBox}
        popupClassName={styles.popover}
        trigger="click"
        arrowPointAtCenter
        popupAlign={popupAlign}
        onVisibleChange={onPopupVisibleChange}
        {...popoverProps}
      >
        {trigger}
      </Popover>
    );
  }
}
