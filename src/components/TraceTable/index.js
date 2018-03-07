import React, { PureComponent } from 'react';
import { Badge, Table, Collapse } from 'antd';
import moment from 'moment';
import TraceStack from '../../components/TraceStack';
import styles from './index.less';

const { Panel } = Collapse;

class TraceTable extends PureComponent {
  handleExtend = (expanded, record) => {
    if (!expanded) {
      return;
    }
    const { traceIds = [] } = record;
    if (traceIds.length < 1) {
      return;
    }
    if (traceIds.length === 1) {
      this.props.onExpand(record.key, traceIds[0]);
    }
  }
  renderExtend = (record) => {
    const { spansContainer = {} } = record;
    const keys = Object.keys(spansContainer);
    const { traceIds = [] } = record;
    const size = traceIds.length;
    if (size < 1) {
      return <span style={{ display: 'none' }} />;
    } else if (size === 1) {
      return (keys.length < 1) ? null : <TraceStack spans={spansContainer[keys[0]]} />;
    }
    return (
      <Collapse
        bordered={false}
        onChange={(key) => {
          if (key.length > 0 && !spansContainer[key]) {
            this.props.onExpand(record.key, key[0]);
          }
        }}
      >
        {traceIds.map((k) => {
          return (
            <Panel header={k} key={k} >
              { spansContainer[k] ? (<TraceStack spans={spansContainer[k]} />) : null }
            </Panel>
          );
          })}
      </Collapse>);
  }
  render() {
    const { data: traces, pagination, loading, onChange } = this.props;

    const columns = [
      {
        title: 'OperationName',
        dataIndex: 'operationName',
      },
      {
        title: 'Duration',
        render: (text, record) => `${record.duration}ms`,
      },
      {
        title: 'StartTime',
        render: (text, record) => {
          return moment(record.startTime).format('YYYY-MM-DD HH:mm:ss.SSS');
        },
      },
      {
        title: 'State',
        render: (text, record) => {
          if (record.isError) {
            return <Badge status="error" text="Error" />;
          } else {
            return <Badge status="success" text="Success" />;
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
          onExpand={this.handleExtend}
          expandedRowRender={this.renderExtend}
        />
      </div>
    );
  }
}

export default TraceTable;
