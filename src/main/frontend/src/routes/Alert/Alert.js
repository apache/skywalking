import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Card, Table, Input } from 'antd';
import styles from './Alert.less';

const { Search } = Input;

@connect(state => ({
  alert: state.alert,
}))
export default class Alert extends PureComponent {
  render() {
    const columns = [{
      title: 'Content',
      dataIndex: 'content',
    }, {
      title: 'Start Time',
      dataIndex: 'time',
    }, {
      title: 'Alert Type',
      dataIndex: 'type',
      filters: [{
        text: 'Application',
        value: 'Application',
      }, {
        text: 'Server',
        value: 'Server',
      }, {
        text: 'Service',
        value: 'Service',
      }],
      filterMultiple: false,
      onFilter: (value, record) => record.type.indexOf(value) === 0,
    }];

    const data = [{
      key: '1',
      content: 'Application alert',
      time: '19:30',
      type: 'Application',
    }, {
      key: '2',
      content: 'Server is down',
      time: '13:30',
      type: 'Server',
    }, {
      key: '3',
      content: 'Server is slow',
      time: '8:30',
      type: 'Service',
    }, {
      key: '4',
      content: 'Service sla is low',
      time: '3:30',
      type: 'Service',
    }];

    function onChange(pagination, filters, sorter) {
      console.log('params', pagination, filters, sorter);
    }

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
          <Table columns={columns} dataSource={data} onChange={onChange} />
        </div>
      </Card>
    );
  }
}
