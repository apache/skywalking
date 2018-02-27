import React, { PureComponent } from 'react';
import { Layout, Icon, Tag, Divider } from 'antd';
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
      isMobile, logo, selectedDuration,
      onDurationToggle, onDurationReload, onRedirect: redirect,
    } = this.props;
    const applications = applicationAlarmList.items.map(_ => ({ ..._, datetime: _.startTime }));
    const servers = serverAlarmList.items.map(_ => ({ ..._, datetime: _.startTime }));
    return (
      <Header className={styles.header}>
        {isMobile && (
          [
            (
              <Link to="/" className={styles.logo} key="logo">
                <img src={logo} alt="logo" width="32" />
              </Link>
            ),
            <Divider type="vertical" key="line" />,
          ]
        )}
        <Icon
          className={styles.trigger}
          type={collapsed ? 'menu-unfold' : 'menu-fold'}
          onClick={this.toggle}
        />
        <div className={styles.right}>
          <DurationIcon
            className={styles.action}
            selectedDuration={selectedDuration}
            onToggle={onDurationToggle}
            onReload={onDurationReload}
          />
          <NoticeIcon
            className={styles.action}
            count={applicationAlarmList.total + serverAlarmList.total}
            onItemClick={(item, tabProps) => {
              redirect({ pathname: '/alarm', state: { type: tabProps.title } });
            }}
            onClear={(tabTitle) => {
              redirect({ pathname: '/alarm', state: { type: tabTitle } });
            }}
            loading={false}
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
              emptyImage="https://gw.alipayobjects.com/zos/rmsportal/wAhyIChODzsoKIOBHcBk.svg"
            />
            <NoticeIcon.Tab
              list={servers}
              title="Server"
              emptyText="No alarm"
              emptyImage="https://gw.alipayobjects.com/zos/rmsportal/wAhyIChODzsoKIOBHcBk.svg"
            />
          </NoticeIcon>
        </div>
      </Header>
    );
  }
}
