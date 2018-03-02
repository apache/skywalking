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
        dataIndex: 'key',
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
            return 'Error';
          } else {
            return 'Success';
          }
        },
      },
      {
        title: 'GlobalTraceId',
        render: (text, record) => {
          const { traceIds = [] } = record;
          if (traceIds.length < 1) {
            return 'Nan';
          }
          if (traceIds.length > 1) {
            return `${traceIds[0]} ...`;
          } else {
            return traceIds[0];
          }
        },
      },
    ];
    return (
      <div className={styles.standardTable}>
        <Table
          loading={loading}
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
