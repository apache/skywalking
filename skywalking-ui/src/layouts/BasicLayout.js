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


import React from 'react';
import PropTypes from 'prop-types';
import { Layout, Icon } from 'antd';
import DocumentTitle from 'react-document-title';
import { connect } from 'dva';
import { Route, Redirect, Switch, routerRedux } from 'dva/router';
import { ContainerQuery } from 'react-container-query';
import classNames from 'classnames';
import lodash from 'lodash';
import GlobalHeader from '../components/GlobalHeader';
import GlobalFooter from '../components/GlobalFooter';
import SiderMenu from '../components/SiderMenu';
import DurationPanel from '../components/Duration/Panel';
import NotFound from '../routes/Exception/404';
import { getRoutes } from '../utils/utils';
import Authorized from '../utils/Authorized';
import { getMenuData } from '../common/menu';
import logo from '../assets/sw-2.png';

const { Content } = Layout;
const { AuthorizedRoute } = Authorized;

const redirectData = [];
const getRedirect = (item) => {
  if (item && item.children) {
    if (item.children[0] && item.children[0].path) {
      redirectData.push({
        from: `/${item.path}`,
        to: `/${item.children[0].path}`,
      });
      item.children.forEach((children) => {
        getRedirect(children);
      });
    }
  }
};
getMenuData().forEach(getRedirect);

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

  getChildContext() {
    const { location, routerData } = this.props;
    return {
      location,
      breadcrumbNameMap: routerData,
    };
  }

  componentWillUpdate(nextProps) {
    const {...propsData} = this.props;
    const { globalVariables: { duration }, isMonitor } = nextProps;
    if (!isMonitor) {
      return;
    }
    if (!duration || Object.keys(duration).length < 1) {
      return;
    }
    const { globalVariables: { duration: preDuration } } = this.props;
    if (duration === preDuration) {
      return;
    }
    propsData.dispatch({
      type: 'global/fetchNotice',
      payload: { variables: { duration } },
    });
  }

  getPageTitle() {
    const { routerData, location } = this.props;
    const { pathname } = location;
    let title = 'Sky Walking';
    if (routerData[pathname] && routerData[pathname].name) {
      title = `${routerData[pathname].name} - SW`;
    }
    return title;
  }

  getBashRedirect = () => {
    // According to the url parameter to redirect
    const urlParams = new URL(window.location.href);

    const redirect = urlParams.searchParams.get('redirect');
    // Remove the parameters in the url
    if (redirect) {
      urlParams.searchParams.delete('redirect');
      window.history.replaceState(null, 'redirect', urlParams.href);
    } else {
      return '/monitor/dashboard';
    }
    return redirect;
  }

  handleDurationToggle = () => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'global/changeDurationCollapsed',
      payload: propsData.duration.collapsed,
    });
  }

  handleDurationReload = () => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'global/reloadDuration',
    });
  }

  handleDurationSelected = (selectedDuration) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'global/changeDuration',
      payload: selectedDuration,
    });
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
    const { step = 0 } = selectedDuration;
    if (step < 1) {
      return;
    }
    this.intervalId = setInterval(this.handleDurationReload, step);
  }

  handleMenuCollapse = (collapsed) => {
    const {...propsData} = this.props;
    propsData.dispatch({
      type: 'global/changeLayoutCollapsed',
      payload: collapsed,
    });
  }

  handleMenuClick = ({ key }) => {
    const {...propsData} = this.props;
    if (key === 'triggerError') {
      propsData.dispatch(routerRedux.push('/exception/trigger'));
      return;
    }
    if (key === 'logout') {
      propsData.dispatch({
        type: 'login/logout',
      });
    }
  }

  handleNoticeVisibleChange = (visible) => {
    const {...propsData} = this.props;
    if (visible) {
      propsData.dispatch({
        type: 'global/fetchNotices',
      });
    }
  }

  handleRedirect = (path) => {
    const { history } = this.props;
    if (history.location.pathname === path.pathname) {
      return;
    }
    history.push(path);
  }

  render() {
    const {...propsData} = this.props;
    const {
      isMonitor, collapsed, fetching, notices, routerData, match, location, zone,
      duration: { selected: dSelected, collapsed: dCollapsed },
    } = this.props;
    const bashRedirect = this.getBashRedirect();
    const layout = (
      <Layout>
        <SiderMenu
          logo={logo}
          // If you do not have the Authorized parameter
          // you will be forced to jump to the 403 interface without permission
          Authorized={Authorized}
          menuData={getMenuData()}
          collapsed={collapsed}
          location={location}
          onCollapse={this.handleMenuCollapse}
        />
        <Layout>
          <GlobalHeader
            logo={logo}
            fetching={fetching}
            notices={notices}
            collapsed={collapsed}
            selectedDuration={dSelected}
            isMonitor={isMonitor}
            onNoticeClear={this.handleNoticeClear}
            onCollapse={this.handleMenuCollapse}
            onMenuClick={this.handleMenuClick}
            onNoticeVisibleChange={this.handleNoticeVisibleChange}
            onDurationToggle={this.handleDurationToggle}
            onDurationReload={this.handleDurationReload}
            onRedirect={this.handleRedirect}
          />
          {isMonitor ? (
            <DurationPanel
              selected={dSelected}
              onSelected={this.handleDurationSelected}
              collapsed={dCollapsed}
              zone={zone}
              dispatch={propsData.dispatch}
            />
          ) : null}
          <Content style={{ margin: '24px 24px 0', height: '100%' }}>
            <Switch>
              {
                redirectData.map(item =>
                  <Redirect key={item.from} exact from={item.from} to={item.to} />
                )
              }
              {
                getRoutes(match.path, routerData).map(item =>
                  (
                    <AuthorizedRoute
                      key={item.key}
                      path={item.path}
                      component={item.component}
                      exact={item.exact}
                      authority={item.authority}
                      redirectPath="/exception/403"
                    />
                  )
                )
              }
              <Redirect exact from="/" to={bashRedirect} />
              <Route render={NotFound} />
            </Switch>
          </Content>
          <GlobalFooter
            links={[{
              key: 'SkyWalking',
              title: 'Apache SkyWalking',
              href: 'http://skywalking.apache.org',
              blankTarget: true,
            }, {
              key: 'GitHub',
              title: 'GitHub',
              href: 'https://github.com/apache/incubator-skywalking',
              blankTarget: true,
            }]}
            copyright={
              <div>
                Copyright <Icon type="copyright" /> 2017 - 2019 The Apache Software Foundation, Licensed under the Apache License, Version 2.0.
              </div>
            }
          />
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

export default connect(({ global, loading }) => ({
  isMonitor: global.isMonitor,
  collapsed: global.collapsed,
  fetching: lodash.values(loading.models).findIndex(_ => _) > -1,
  notices: global.notices,
  duration: global.duration,
  globalVariables: global.globalVariables,
  zone: global.zone,
}))(BasicLayout);
