import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Card, Form } from 'antd';
import {
  ChartCard, MiniArea, MiniBar, Line, Area, StackBar,
} from '../../components/Charts';
import DescriptionList from '../../components/DescriptionList';
import { axis } from '../../utils/time';
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
    const { form, duration, server } = this.props;
    const { getFieldDecorator } = form;
    const { variables: { values }, data } = server;
    const { serverInfo, getServerResponseTimeTrend, getServerTPSTrend,
      getCPUTrend, getMemoryTrend, getGCTrend } = data;
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
                contentHeight={46}
              >
                {getServerResponseTimeTrend.trendList.length > 0 ? (
                  <MiniArea
                    animate={false}
                    color="#975FE4"
                    data={axis(duration, getServerResponseTimeTrend.trendList)}
                  />
                ) : (<span style={{ display: 'none' }} />)}
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
                  data={axis(duration, getServerTPSTrend.trendList)}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
              <ChartCard
                title="CPU"
                contentHeight={150}
              >
                <Line
                  data={axis(duration, getCPUTrend.cost)}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="Heap"
                contentHeight={150}
              >
                <Area
                  data={axis(duration, getMemoryTrend.heap, ({ x, y }) => ({ x, y, type: 'value' }))
                    .concat(axis(duration, getMemoryTrend.maxHeap, ({ x, y }) => ({ x, y, type: 'limit' })))}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={12} lg={12} xl={12} style={{ marginTop: 24 }}>
              <ChartCard
                title="No-Heap"
                contentHeight={150}
              >
                <Area
                  data={axis(duration, getMemoryTrend.noheap, ({ x, y }) => ({ x, y, type: 'value' }))
                  .concat(axis(duration, getMemoryTrend.maxNoheap, ({ x, y }) => ({ x, y, type: 'limit' })))}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
              <ChartCard
                title="GC"
                contentHeight={150}
              >
                <StackBar
                  data={axis(duration, getGCTrend.oldGC, ({ x, y }) => ({ x, y, type: 'oldGC' }))
                  .concat(axis(duration, getGCTrend.youngGC, ({ x, y }) => ({ x, y, type: 'youngGC' })))}
                />
              </ChartCard>
            </Col>
          </Row>
        </Panel>
      </div>
    );
  }
}
