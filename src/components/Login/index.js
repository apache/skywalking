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
import PropTypes from 'prop-types';
import { Form, Tabs } from 'antd';
import classNames from 'classnames';
import LoginItem from './LoginItem';
import LoginTab from './LoginTab';
import LoginSubmit from './LoginSubmit';
import styles from './index.less';

class Login extends Component {
  static propTypes = {
    className: PropTypes.string,
    defaultActiveKey: PropTypes.string,
    onTabChange: PropTypes.func,
    onSubmit: PropTypes.func,
  };

  static childContextTypes = {
    tabUtil: PropTypes.object,
    form: PropTypes.object,
    updateActive: PropTypes.func,
  };

  static defaultProps = {
    className: '',
    defaultActiveKey: '',
    onTabChange: () => {},
    onSubmit: () => {},
  };

  state = {
    type: this.props.defaultActiveKey,
    tabs: [],
    active: {},
  };
  getChildContext() {
    return {
      tabUtil: {
        addTab: (id) => {
          this.setState({
            tabs: [...this.state.tabs, id],
          });
        },
        removeTab: (id) => {
          this.setState({
            tabs: this.state.tabs.filter(currentId => currentId !== id),
          });
        },
      },
      form: this.props.form,
      updateActive: (activeItem) => {
        const { type, active } = this.state;
        if (active[type]) {
          active[type].push(activeItem);
        } else {
          active[type] = [activeItem];
        }
        this.setState({
          active,
        });
      },
    };
  }
  onSwitch = (type) => {
    this.setState({
      type,
    });
    this.props.onTabChange(type);
  };
  handleSubmit = (e) => {
    e.preventDefault();
    const { active, type } = this.state;
    const activeFileds = active[type];
    this.props.form.validateFields(activeFileds, { force: true }, (err, values) => {
      this.props.onSubmit(err, values);
    });
  };
  render() {
    const { className, children } = this.props;
    const { type, tabs } = this.state;
    const TabChildren = [];
    const otherChildren = [];
    React.Children.forEach(children, (item) => {
      if (!item) {
        return;
      }
      // eslint-disable-next-line
      if (item.type.__ANT_PRO_LOGIN_TAB) {
        TabChildren.push(item);
      } else {
        otherChildren.push(item);
      }
    });
    return (
      <div className={classNames(className, styles.login)}>
        <Form onSubmit={this.handleSubmit}>
          {tabs.length ? (
            <div>
              <Tabs
                animated={false}
                className={styles.tabs}
                activeKey={type}
                onChange={this.onSwitch}
              >
                {TabChildren}
              </Tabs>
              {otherChildren}
            </div>
          ) : (
            [...children]
          )}
        </Form>
      </div>
    );
  }
}

Login.Tab = LoginTab;
Login.Submit = LoginSubmit;
Object.keys(LoginItem).forEach((item) => {
  Login[item] = LoginItem[item];
});

export default Form.create()(Login);
