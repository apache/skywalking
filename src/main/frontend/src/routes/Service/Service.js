import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Card, Form } from 'antd';
import {
  ChartCard, MiniArea, MiniBar,
} from '../../components/Charts';
import { timeRange } from '../../utils/utils';
import { ServiceTopology } from '../../components/Topology';
import { Panel, Search } from '../../components/Page';

const { Item: FormItem } = Form;

@connect(state => ({
  service: state.service,
  duration: state.global.duration,
  globalVariables: state.global.globalVariables,
}))
@Form.create({
  mapPropsToFields(props) {
    const { variables: { values, labels } } = props.service;
    return {
      serviceId: Form.createFormField({
        value: { key: values.serviceId ? values.serviceId : '', label: labels.serviceId ? labels.serviceId : '' },
      }),
    };
  },
})
export default class Service extends PureComponent {
  handleSelect = (selected) => {
    this.props.dispatch({
      type: 'service/save',
      payload: {
        variables: {
          values: { serviceId: selected.key },
          labels: { serviceId: selected.label },
        },
        data: {
          serviceInfo: selected,
        },
      },
    });
  }
  handleChange = (variables) => {
    this.props.dispatch({
      type: 'service/fetchData',
      payload: { variables },
    });
  }
  avg = list => (list.length > 0 ?
    (list.reduce((acc, curr) => acc + curr) / list.length).toFixed(2) : 0)
  render() {
    const { getFieldDecorator } = this.props.form;
    const { variables: { values }, data } = this.props.service;
    const { getServiceResponseTimeTrend, getServiceTPSTrend,
      getServiceSLATrend, getServiceTopology } = data;
    const timeRangeArray = timeRange(this.props.duration);
    return (
      <div>
        <Form layout="inline">
          <FormItem>
            {getFieldDecorator('serviceId')(
              <Search
                placeholder="Search a service"
                onSelect={this.handleSelect.bind(this)}
                url="/service/search"
                query={`
                  query SearchService($keyword: String!) {
                    searchService(keyword: $keyword, topN: 10) {
                      key: id
                      label: name
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
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Throughout"
                total={`${this.avg(getServiceTPSTrend.trendList)}`}
              >
                <MiniArea
                  animate={false}
                  color="#975FE4"
                  height={46}
                  data={getServiceTPSTrend.trendList
                    .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Response Time"
                total={`${this.avg(getServiceResponseTimeTrend.trendList)} ms`}
              >
                <MiniArea
                  animate={false}
                  height={46}
                  data={getServiceResponseTimeTrend.trendList
                    .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg SLA"
                total={`${this.avg(getServiceSLATrend.trendList)} %`}
              >
                <MiniBar
                  animate={false}
                  height={46}
                  data={getServiceSLATrend.trendList
                    .map((v, i) => { return { x: timeRangeArray[i], y: v }; })}
                />
              </ChartCard>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col xs={24} sm={24} md={24} lg={24} xl={24} style={{ marginTop: 24 }}>
              <Card
                bordered={false}
                bodyStyle={{ padding: 0 }}
              >
                <ServiceTopology elements={getServiceTopology} layout={{ name: 'concentric', minNodeSpacing: 200 }} />
              </Card>
            </Col>
          </Row>
        </Panel>
      </div>
    );
  }
}
