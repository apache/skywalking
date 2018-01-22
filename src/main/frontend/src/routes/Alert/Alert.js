import React, { Component } from 'react';
import { connect } from 'dva';
import { Card, Input, Tabs, List, Avatar } from 'antd';
import styles from './Alert.less';

const { Search } = Input;
const { TabPane } = Tabs;

@connect(state => ({
  alert: state.alert,
  duration: state.global.duration,
  loading: state.loading.models.alarm,
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
  changeAlarmType = () => {}
  renderList = (data) => {
    const { loading } = this.props;
    return (
      <List
        className="demo-loadmore-list"
        loading={loading}
        itemLayout="horizontal"
        dataSource={data}
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
          onSearch={() => ({})}
        />
      </div>
    );
    const { alert: { loadAlertList } } = this.props;
    return (
      <Card
        title="Alarm List"
        bordered={false}
        extra={extraContent}
      >
        <Tabs defaultActiveKey="1" onChange={this.changeAlarmType}>
          <TabPane tab="Application" key="1">{this.renderList(loadAlertList.filter(i => i.alertType === 'APPLICATION'))}</TabPane>
          <TabPane tab="Server" key="2">{this.renderList(loadAlertList.filter(i => i.alertType === 'SERVER'))}</TabPane>
          <TabPane tab="Service" key="3">{this.renderList(loadAlertList.filter(i => i.alertType === 'SERVICE'))}</TabPane>
        </Tabs>
      </Card>
    );
  }
}
