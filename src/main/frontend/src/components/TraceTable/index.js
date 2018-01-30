import React, { PureComponent } from 'react';
import { Table } from 'antd';
import TraceStack from '../../components/TraceStack';
import styles from './index.less';

class TraceTable extends PureComponent {
  render() {
    const { data: traces, pagination, loading, onExpand, onChange } = this.props;

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
        render: (text, record) => {
          if (record.isError) {
            return 'Success';
          } else {
            return 'Error';
          }
        },
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
          rowKey={record => record.traceId}
          dataSource={traces}
          columns={columns}
          pagination={pagination}
          onChange={onChange}
          onExpand={onExpand}
          expandedRowRender={record => (record.spans ? <TraceStack spans={record.spans} /> : null)}
        />
      </div>
    );
  }
}

export default TraceTable;
