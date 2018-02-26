import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Card, Form } from 'antd';
import {
  ChartCard, MiniArea, MiniBar, Line, Area, StackBar,
} from '../../components/Charts';
import DescriptionList from '../../components/DescriptionList';
import { timeRange } from '../../utils/time';
import { Panel, Search } from '../../components/Page';

const { Description } = DescriptionList;
const { Item: FormItem } = Form;

@connect(state => ({
  server: state.server,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.server;
    return {
      serverId: Form.createFormField({
        value: { key: values.serverId ? values.serverId : '', label: labels.serverId ? labels.serverId : '' },
      }),
    };
  },
})
export default class Server extends PureComponent {
  handleSelect = (selected) => {
    this.props.dispatch({
      type: 'server/save',
      payload: {
        variables: {
          values: { serverId: selected.key },
          labels: { serverId: selected.label },
        },
        data: {
          serverInfo: selected,
        },
      },
    });
  }
  handleChange = (variables) => {
    this.props.dispatch({
      type: 'server/fetchData',
      payload: { variables },
    });
  }
  avg = list => (list.length > 0 ?
    (list.reduce((acc, curr) => acc + curr) / list.length).toFixed(2) : 0)
  render() {
    const { getFieldDecorator } = this.props.form;
    const { variables: { values }, data } = this.props.server;
    const { serverInfo, getServerResponseTimeTrend, getServerTPSTrend,
      getCPUTrend, getMemoryTrend, getGCTrend } = data;
    const timeRangeArray = timeRange(this.props.duration);
    return (
      <div>
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('serverId')(
              <Search
                placeholder="Select a server"
                onSelect={this.handleSelect.bind(this)}
                url="/server/search"
                variables={{ duration: this.props.globalVariables.duration }}
                query={`
                  query SearchServer($keyword: String!, $duration: Duration!) {
                    searchServer(keyword: $keyword, duration: $duration) {
                      key: id
                      label: name
                      host
                      pid
                      ipv4
                    }
                  }
                `}
              />
            )}
          </FormItem>
        </Form>
        <Panel
          variables={values}
          globalVariables={this.props.globalVariables}
          onChange={this.handleChange}
        >
          <Card title="Info" style={{ marginTop: 24 }} bordered={false}>
            <DescriptionList>
              <Description term="OS">{serverInfo.label}</Description>
              <Description term="Host Name">{serverInfo.host}</Description>
              <Description term="Process Id">{serverInfo.pid}</Description>
              <Description term="IPv4">{serverInfo.ipv4 ? serverInfo.ipv4.join() : ''}</Description>
            </DescriptionList>
          </Card>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Response Time"
                total={`${this.avg(getServerResponseTimeTrend.trendList)} ms`}
              >
                <MiniArea
                  animate={false}
                  color="#975FE4"
                  height={46}
                  data={getServerResponseTimeTrend.trendList
                    .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg TPS"
                total={`${this.avg(getServerTPSTrend.trendList)} ms`}
              >
                <MiniBar
                  animate={false}
                  height={46}
                  data={getServerTPSTrend.trendList
                    .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
              <Card
                title="CPU"
                bordered={false}
                bodyStyle={{ padding: 0 }}
              >
                <Line
                  height={250}
                  data={getCPUTrend.cost
                    .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
                />
              </Card>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
              <Card
                title="Heap"
                bordered={false}
                bodyStyle={{ padding: 0 }}
              >
                <Area
                  height={250}
                  data={getMemoryTrend.heap
                    .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'value' }))
                    .concat(getMemoryTrend.maxHeap
                    .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'limit ' })))}
                />
              </Card>
            </Col>
            <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
              <Card
                title="No-Heap"
                bordered={false}
                bodyStyle={{ padding: 0 }}
              >
                <Area
                  height={250}
                  data={getMemoryTrend.noheap
                    .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'value' }))
                    .concat(getMemoryTrend.maxNoheap
                    .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'limit ' })))}
                />
              </Card>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
              <Card
                title="GC"
                bordered={false}
                bodyStyle={{ padding: 0 }}
              >
                <StackBar
                  height={250}
                  data={getGCTrend.oldGC
                    .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'oldGC' }))
                    .concat(getGCTrend.youngGC
                    .map((v, i) => ({ x: timeRangeArray[i], y: v, type: 'youngGC' })))}
                />
              </Card>
            </Col>
          </Row>
        </Panel>
      </div>
    );
  }
}
