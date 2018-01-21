import React from 'react';
import PropTypes from 'prop-types';
import DocumentTitle from 'react-document-title';

import { connect } from 'dva';
import { Link, Route, Redirect, Switch } from 'dva/router';
import { Layout, Menu, Icon, Tag } from 'antd';

import NoticeIcon from 'ant-design-pro/lib/NoticeIcon';
import GlobalFooter from 'ant-design-pro/lib/GlobalFooter';

import moment from 'moment';
import groupBy from 'lodash/groupBy';
import classNames from 'classnames';
import { ContainerQuery } from 'react-container-query';

import styles from './BasicLayout.less';

import TimeSelect from '../components/Time/TimeSelect';

const { Header, Sider, Content } = Layout;
const { SubMenu } = Menu;


const query = {
  'screen-xs': {
    maxWidth: 575,
  },
  'screen-sm': {
    minWidth: 576,
    maxWidth: 767,
  },
  'screen-md': {
    minWidth: 768,
    maxWidth: 991,
  },
  'screen-lg': {
    minWidth: 992,
    maxWidth: 1199,
  },
  'screen-xl': {
    minWidth: 1200,
  },
};

class BasicLayout extends React.PureComponent {
  static childContextTypes = {
    location: PropTypes.object,
    breadcrumbNameMap: PropTypes.object,
  }
  constructor(props) {
    super(props);
    // 把一级 Layout 的 children 作为菜单项
    this.menus = props.navData.reduce((arr, current) => arr.concat(current.children), []);
    this.state = {
      openKeys: this.getDefaultCollapsedSubMenus(props),
    };
  }
  getChildContext() {
    const { location, navData, getRouteData } = this.props;
    const routeData = getRouteData('BasicLayout');
    const firstMenuData = navData.reduce((arr, current) => arr.concat(current.children), []);
    const menuData = this.getMenuData(firstMenuData, '');
    const breadcrumbNameMap = {};

    routeData.concat(menuData).forEach((item) => {
      breadcrumbNameMap[item.path] = item.name;
    });
    return { location, breadcrumbNameMap };
  }
  componentDidMount() {
  }
  componentWillUnmount() {
    clearTimeout(this.resizeTimeout);
  }
  onCollapse = (collapsed) => {
    this.props.dispatch({
      type: 'global/changeLayoutCollapsed',
      payload: collapsed,
    });
  }
  onMenuClick = ({ key }) => {
    if (key === 'logout') {
      this.props.dispatch({
        type: 'login/logout',
      });
    }
  }
  getMenuData = (data, parentPath) => {
    let arr = [];
    data.forEach((item) => {
      if (item.children) {
        arr.push({ path: `${parentPath}/${item.path}`, name: item.name });
        arr = arr.concat(this.getMenuData(item.children, `${parentPath}/${item.path}`));
      }
    });
    return arr;
  }
  getDefaultCollapsedSubMenus(props) {
    const currentMenuSelectedKeys = [...this.getCurrentMenuSelectedKeys(props)];
    currentMenuSelectedKeys.splice(-1, 1);
    if (currentMenuSelectedKeys.length === 0) {
      return ['dashboard'];
    }
    return currentMenuSelectedKeys;
  }
  getCurrentMenuSelectedKeys(props) {
    const { location: { pathname } } = props || this.props;
    const keys = pathname.split('/').slice(1);
    if (keys.length === 1 && keys[0] === '') {
      return [this.menus[0].key];
    }
    return keys;
  }
  getNavMenuItems(menusData, parentPath = '') {
    if (!menusData) {
      return [];
    }
    return menusData.map((item) => {
      if (!item.name) {
        return null;
      }
      let itemPath;
      if (item.path.indexOf('http') === 0) {
        itemPath = item.path;
      } else {
        itemPath = `${parentPath}/${item.path || ''}`.replace(/\/+/g, '/');
      }
      if (item.children && item.children.some(child => child.name)) {
        return (
          <SubMenu
            title={
              item.icon ? (
                <span>
                  {item.icon.indexOf('iconfont') > -1 ? (<i className={item.icon} />) : (<Icon type={item.icon} />)}
                  <span>{item.name}</span>
                </span>
              ) : item.name
            }
            key={item.key || item.path}
          >
            {this.getNavMenuItems(item.children, itemPath)}
          </SubMenu>
        );
      }
      const icon = item.icon && (item.icon.indexOf('iconfont') > -1 ? (<i className={item.icon} />) : (<Icon type={item.icon} />));
      return (
        <Menu.Item key={item.key || item.path}>
          {
            /^https?:\/\//.test(itemPath) ? (
              <a href={itemPath} target={item.target}>
                {icon}<span>{item.name}</span>
              </a>
            ) : (
              <Link
                to={itemPath}
                target={item.target}
                replace={itemPath === this.props.location.pathname}
              >
                {icon}<span>{item.name}</span>
              </Link>
            )
          }
        </Menu.Item>
      );
    });
  }
  getPageTitle() {
    const { location, getRouteData } = this.props;
    const { pathname } = location;
    let title = 'SkyWalking';
    getRouteData('BasicLayout').forEach((item) => {
      if (item.path === pathname) {
        title = `${item.name} - SkyWalking`;
      }
    });
    return title;
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
  handleOpenChange = (openKeys) => {
    const lastOpenKey = openKeys[openKeys.length - 1];
    const isMainMenu = this.menus.some(
      item => lastOpenKey && (item.key === lastOpenKey || item.path === lastOpenKey)
    );
    this.setState({
      openKeys: isMainMenu ? [lastOpenKey] : [...openKeys],
    });
  }
  toggle = () => {
    const { collapsed } = this.props;
    this.props.dispatch({
      type: 'global/changeLayoutCollapsed',
      payload: !collapsed,
    });
    this.resizeTimeout = setTimeout(() => {
      const event = document.createEvent('HTMLEvents');
      event.initEvent('resize', true, false);
      window.dispatchEvent(event);
    }, 600);
  }
  handleTimeSelected = (selectedTime) => {
    this.props.dispatch({
      type: 'global/changeSelectedTime',
      payload: selectedTime,
    });
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
    const { step = 0 } = selectedTime;
    if (step < 1) {
      return;
    }
    this.intervalId = setInterval(this.reload, step);
  }
  reload = () => {
    this.props.dispatch({
      type: 'global/reload',
    });
  }
  toggleSelectTime = () => {
    this.props.dispatch({
      type: 'global/toggleSelectTime',
    });
  }
  render() {
    const { collapsed, getRouteData } = this.props;
    // Don't show popup menu when it is been collapsed
    const menuProps = collapsed ? {} : {
      openKeys: this.state.openKeys,
    };
    const { selectedTime = {
      from() {
        return moment();
      },
      to() {
        return moment();
      },
      lable: 'NaN',
    } } = this.props;
    const timeFormat = 'YYYY-MM-DD HH:mm:ss';

    const layout = (
      <Layout>
        <Sider
          trigger={null}
          collapsible
          collapsed={collapsed}
          breakpoint="md"
          onCollapse={this.onCollapse}
          width={256}
          className={styles.sider}
        >
          <div className={styles.logo}>
            <Link to="/">
              <img src="img/logo/sw-2.png" alt="logo" style={{ width: 50, height: 30 }} />
              <h1>SkyWalking</h1>
            </Link>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            {...menuProps}
            onOpenChange={this.handleOpenChange}
            selectedKeys={this.getCurrentMenuSelectedKeys()}
            style={{ margin: '16px 0', width: '100%' }}
          >
            {this.getNavMenuItems(this.menus)}
          </Menu>
        </Sider>
        <Layout>
          <Header className={styles.header}>
            <Icon
              className={styles.trigger}
              type={collapsed ? 'menu-unfold' : 'menu-fold'}
              onClick={this.toggle}
            />
            <div className={styles.right}>
              <span
                className={styles.action}
                onClick={this.toggleSelectTime}
              >
                {selectedTime.label ? selectedTime.label : `${selectedTime.from().format(timeFormat)} ~ ${selectedTime.to().format(timeFormat)}`}
                {selectedTime.step > 0 ? ` Reloading every ${selectedTime.step / 1000} seconds` : null }
              </span>
              <span className={styles.action} onClick={this.reload}> <Icon type="reload" /> </span>
              <NoticeIcon
                className={styles.action}
                count={3}
                onItemClick={(item, tabProps) => {
                  console.log(item, tabProps); // eslint-disable-line
                }}
                loading={false}
                popupAlign={{ offset: [20, -16] }}
                locale={{
                  emptyText: 'No alert',
                  clear: 'More ',
                }}
              >
                <NoticeIcon.Tab
                  list={[{
                    id: '000000001',
                    avatar: 'https://gw.alipayobjects.com/zos/rmsportal/ThXAXghbEsBCCSDihZxY.png',
                    title: 'Appliction A error',
                    datetime: '2017-08-09',
                    type: 'app-alert',
                  }, {
                    id: '000000002',
                    avatar: 'https://gw.alipayobjects.com/zos/rmsportal/OKJXDXrmkNshAMvwtvhu.png',
                    title: 'Appliction A error',
                    datetime: '2017-08-08',
                    type: 'app-alert',
                  }, {
                    id: '000000003',
                    avatar: 'https://gw.alipayobjects.com/zos/rmsportal/kISTdvpyTAhtGxpovNWd.png',
                    title: 'Appliction A error',
                    datetime: '2017-08-07',
                    read: true,
                    type: 'app-alert',
                  }]}
                  title="Application Alert"
                  emptyText="No alert"
                  emptyImage="https://gw.alipayobjects.com/zos/rmsportal/wAhyIChODzsoKIOBHcBk.svg"
                />
                <NoticeIcon.Tab
                  list={[{
                    id: '000000001',
                    avatar: 'https://gw.alipayobjects.com/zos/rmsportal/ThXAXghbEsBCCSDihZxY.png',
                    title: 'Server A error',
                    datetime: '2017-08-09',
                    type: 'server-alert',
                  }, {
                    id: '000000002',
                    avatar: 'https://gw.alipayobjects.com/zos/rmsportal/OKJXDXrmkNshAMvwtvhu.png',
                    title: 'Server A error',
                    datetime: '2017-08-08',
                    type: 'server-alert',
                  }, {
                    id: '000000003',
                    avatar: 'https://gw.alipayobjects.com/zos/rmsportal/kISTdvpyTAhtGxpovNWd.png',
                    title: 'Service A error',
                    datetime: '2017-08-07',
                    read: true,
                    type: 'server-alert',
                  }]}
                  title="Server Alert"
                  emptyText="No alert"
                  emptyImage="https://gw.alipayobjects.com/zos/rmsportal/wAhyIChODzsoKIOBHcBk.svg"
                />
              </NoticeIcon>
            </div>
          </Header>
          <TimeSelect
            selectedTime={this.props.selectedTime}
            onSelected={this.handleTimeSelected}
            isShow={this.props.isShowSelectTime}
          />
          <Content style={{ margin: '24px 24px 0', height: '100%' }}>
            <Switch>
              {
                getRouteData('BasicLayout').map(item =>
                  (
                    <Route
                      exact={item.exact}
                      key={item.path}
                      path={item.path}
                      component={item.component}
                    />
                  )
                )
              }
              <Redirect exact from="/" to="/dashboard" />
            </Switch>
            <GlobalFooter
              links={[{
                title: 'SkyWalking',
                href: 'http://skywalking.io',
                blankTarget: true,
              }, {
                title: 'GitHub',
                href: 'https://github.com/apache/incubator-skywalking',
                blankTarget: true,
              }]}
              copyright={
                <div>
                  Copyright <Icon type="copyright" /> 2018 SkyWalking
                </div>
              }
            />
          </Content>
        </Layout>
      </Layout>
    );

    return (
      <DocumentTitle title={this.getPageTitle()}>
        <ContainerQuery query={query}>
          {params => <div className={classNames(params)}>{layout}</div>}
        </ContainerQuery>
      </DocumentTitle>
    );
  }
}

export default connect(state => ({
  currentUser: state.user.currentUser,
  collapsed: state.global.collapsed,
  fetchingNotices: state.global.fetchingNotices,
  notices: state.global.notices,
  selectedTime: state.global.selectedTime,
  isShowSelectTime: state.global.isShowSelectTime,
}))(BasicLayout);
