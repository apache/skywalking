import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Card, Input, Tabs, List, Avatar } from 'antd';
import { Panel } from '../../components/Page';
import styles from './Alarm.less';

const { Search } = Input;
const { TabPane } = Tabs;
const defaultPaging = {
  pageNum: 1,
  pageSize: 10,
  needTotal: true,
};

@connect(state => ({
  alarm: state.alarm,
  globalVariables: state.global.globalVariables,
  loading: state.loading.models.alarm,
}))
export default class Alarm extends PureComponent {
  componentDidMount() {
    const { alarm: { variables: { values } } } = this.props;
    if (!values.alarmType) {
      this.props.dispatch({
        type: 'alarm/saveVariables',
        payload: { values: {
          alarmType: 'APPLICATION',
          paging: defaultPaging,
        } },
      });
    }
  }
  handleSearch = (keyword) => {
    this.props.dispatch({
      type: 'alarm/saveVariables',
      payload: { values: {
        keyword,
        paging: defaultPaging,
      } },
    });
  }
  handlePageChange = (pag) => {
    this.props.dispatch({
      type: 'alarm/saveVariables',
      payload: { values: {
        paging: {
          pageNum: pag,
          pageSize: 10,
          needTotal: true,
        },
      } },
    });
  }
  changeAlarmType = (alarmType) => {
    this.props.dispatch({
      type: 'alarm/saveVariables',
      payload: { values: {
        alarmType,
        paging: defaultPaging,
      } },
    });
  }
  handleChange = (variables) => {
    const type = variables.alarmType.charAt(0) + variables.alarmType.slice(1).toLowerCase();
    this.props.dispatch({
      type: 'alarm/fetchData',
      payload: { variables, reducer: `save${type}AlarmList` },
    });
  }
  renderList = ({ items, total }) => {
    const { alarm: { variables: { values: { paging = defaultPaging } } }, loading } = this.props;
    const pagination = {
      pageSize: paging.pageSize,
      current: paging.pageNum,
      total,
      onChange: this.handlePageChange,
    };
    return (
      <List
        className="demo-loadmore-list"
        loading={loading}
        itemLayout="horizontal"
        dataSource={items}
        pagination={pagination}
        renderItem={item => (
          <List.Item>
            <List.Item.Meta
              avatar={
                <Avatar
                  style={item.causeType === 'LOW_SUCCESS_RATE' ? { backgroundColor: '#e68a00' } : { backgroundColor: '#b32400' }}
                  icon={item.causeType === 'LOW_SUCCESS_RATE' ? 'clock-circle-o' : 'close'}
                />}
              title={item.title}
              description={item.content}
            />
            <div>{item.startTime}</div>
          </List.Item>
        )}
      />);
  }
  render() {
    const extraContent = (
      <div className={styles.extraContent}>
        <Search
          className={styles.extraContentSearch}
          placeholder="Search..."
          onSearch={this.handleSearch}
        />
      </div>
    );
    const { alarm: { variables: { values }, data } } = this.props;
    return (
      <Panel
        variables={values}
        globalVariables={this.props.globalVariables}
        onChange={this.handleChange}
      >
        <Card
          title="Alarm List"
          bordered={false}
          extra={extraContent}
        >
          <Tabs activeKey={values.alarmType} onChange={this.changeAlarmType}>
            <TabPane tab="Application" key="APPLICATION">{this.renderList(data.applicationAlarmList)}</TabPane>
            <TabPane tab="Server" key="SERVER">{this.renderList(data.serverAlarmList)}</TabPane>
            <TabPane tab="Service" key="SERVICE">{this.renderList(data.serviceAlarmList)}</TabPane>
          </Tabs>
        </Card>
      </Panel>
    );
  }
}
