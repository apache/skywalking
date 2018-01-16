import React, { PureComponent } from 'react';
import { Table } from 'antd';
import styles from './index.less';

class TraceTable extends PureComponent {
  handleTableChange = (pagination, filters, sorter) => {
    this.props.onChange(pagination, filters, sorter);
  }

  render() {
    const { data: { traces, pagination }, loading } = this.props;

    const columns = [
      {
        title: 'OperationName',
        dataIndex: 'operationName',
      },
      {
        title: 'Duration',
        dataIndex: 'duration',
      },
      {
        title: 'StartTime',
        dataIndex: 'start',
      },
      {
        title: 'State',
        dataIndex: 'isError',
        filters: [
          {
            text: 'Error',
            value: true,
          },
          {
            text: 'Success',
            value: false,
          },
        ],
      },
      {
        title: 'GlobalTraceId',
        dataIndex: 'traceId',
      },
    ];

    return (
      <div className={styles.standardTable}>
        <Table
          loading={loading}
          rowKey={record => record.key}
          dataSource={traces}
          columns={columns}
          pagination={pagination}
          onChange={this.handleTableChange}
        />
      </div>
    );
  }
}

export default TraceTable;
