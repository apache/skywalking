import React, { Component } from 'react';
import { connect } from 'dva';
import { Card, Table, Input } from 'antd';
import styles from './Alert.less';

const { Search } = Input;

@connect(state => ({
  alert: state.alert,
  duration: state.global.duration,
}))
export default class Alert extends Component {
  componentDidMount() {
    this.props.dispatch({
      type: 'alert/fetch',
      payload: {},
    });
  }
  shouldComponentUpdate(nextProps) {
    if (this.props.duration !== nextProps.duration) {
      this.props.dispatch({
        type: 'alert/fetch',
        payload: {},
      });
    }
    return this.props.alert !== nextProps.alert;
  }
  render() {
    const columns = [{
      title: 'Content',
      dataIndex: 'content',
    }, {
      title: 'Start Time',
      dataIndex: 'startTime',
    }, {
      title: 'Alert Type',
      dataIndex: 'alertType',
      filters: [{
        text: 'APPLICATION',
        value: 'APPLICATION',
      }, {
        text: 'SERVER',
        value: 'SERVER',
      }, {
        text: 'SERVICE',
        value: 'SERVICE',
      }],
      filterMultiple: false,
      onFilter: (value, record) => record.alertType.indexOf(value) === 0,
    }];
    const extraContent = (
      <div className={styles.extraContent}>
        <Search
          className={styles.extraContentSearch}
          placeholder="Search..."
          onSearch={() => ({})}
        />
      </div>
    );

    return (
      <Card
        title="Alert List"
        bordered={false}
        extra={extraContent}
      >
        <div className={styles.tableList}>
          <Table columns={columns} dataSource={this.props.alert.loadAlertList} />
        </div>
      </Card>
    );
  }
}
