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
import { Layout, Icon, Tag, Divider, Dropdown, Avatar, Menu } from 'antd';
import moment from 'moment';
import groupBy from 'lodash/groupBy';
import Debounce from 'lodash-decorators/debounce';
import { Link } from 'dva/router';
import NoticeIcon from '../NoticeIcon';
import DurationIcon from '../Duration/Icon';
import styles from './index.less';

const { Header } = Layout;

export default class GlobalHeader extends PureComponent {
  componentWillUnmount() {
    this.triggerResizeEvent.cancel();
  }
  getNoticeData() {
    const { notices = [] } = this.props;
    if (notices.length === 0) {
      return {};
    }
    const newNotices = notices.map((notice) => {
      const newNotice = { ...notice };
      if (newNotice.datetime) {
        newNotice.datetime = moment(notice.datetime).fromNow();
      }
      // transform id to item key
      if (newNotice.id) {
        newNotice.key = newNotice.id;
      }
      if (newNotice.extra && newNotice.status) {
        const color = ({
          todo: '',
          processing: 'blue',
          urgent: 'red',
          doing: 'gold',
        })[newNotice.status];
        newNotice.extra = <Tag color={color} style={{ marginRight: 0 }}>{newNotice.extra}</Tag>;
      }
      return newNotice;
    });
    return groupBy(newNotices, 'type');
  }
  toggle = () => {
    const { collapsed, onCollapse } = this.props;
    onCollapse(!collapsed);
    this.triggerResizeEvent();
  }
  @Debounce(600)
  triggerResizeEvent() { // eslint-disable-line
    const event = document.createEvent('HTMLEvents');
    event.initEvent('resize', true, false);
    window.dispatchEvent(event);
  }
  render() {
    const {
      collapsed, notices: { applicationAlarmList, serverAlarmList },
      logo, selectedDuration, fetching, isMonitor,
      onDurationToggle, onDurationReload, onRedirect: redirect,
      onMenuClick,
    } = this.props;
    const applications = applicationAlarmList.items.map(_ => ({ ..._, datetime: _.startTime }));
    const servers = serverAlarmList.items.map(_ => ({ ..._, datetime: _.startTime }));
    const menu = (
      <Menu className={styles.menu} selectedKeys={[]} onClick={onMenuClick}>
        <Menu.Item key="logout">
          <Icon type="logout" />Logout
        </Menu.Item>
      </Menu>
    );
    return (
      <Header className={styles.header}>
        <Link to="/" className={styles.logo} key="logo">
          <img src={logo} alt="logo" width="50" />
        </Link>
        <Divider type="vertical" key="line" />
        <Icon
          className={styles.trigger}
          type={collapsed ? 'menu-unfold' : 'menu-fold'}
          onClick={this.toggle}
        />
        { isMonitor ? (
          <div className={styles.right}>
            <DurationIcon
              loading={fetching}
              className={styles.action}
              selectedDuration={selectedDuration}
              onToggle={onDurationToggle}
              onReload={onDurationReload}
            />
            <NoticeIcon
              className={styles.action}
              count={applicationAlarmList.total + serverAlarmList.total}
              onItemClick={(item, tabProps) => {
                redirect({ pathname: '/monitor/alarm', state: { type: tabProps.title } });
              }}
              onClear={(tabTitle) => {
                redirect({ pathname: '/monitor/alarm', state: { type: tabTitle } });
              }}
              loading={fetching}
              popupAlign={{ offset: [20, -16] }}
              locale={{
                emptyText: 'No alert',
                clear: 'More ',
              }}
            >
              <NoticeIcon.Tab
                list={applications}
                title="Application"
                emptyText="No alarm"
                emptyImage="alarm-backgroud.png"
              />
              <NoticeIcon.Tab
                list={servers}
                title="Server"
                emptyText="No alarm"
                emptyImage="alarm-backgroud.png"
              />
            </NoticeIcon>
            <Dropdown overlay={menu}>
              <span className={`${styles.action} ${styles.account}`}>
                <Avatar size="small" className={styles.avatar} icon="user" />
              </span>
            </Dropdown>
          </div>
        ) : null}
      </Header>
    );
  }
}
