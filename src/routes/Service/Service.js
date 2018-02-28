import React, { PureComponent } from 'react';
import { connect } from 'dva';
import { Row, Col, Card, Form } from 'antd';
import {
  ChartCard, MiniArea, MiniBar,
} from '../../components/Charts';
import { axis } from '../../utils/time';
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
    const { form, service, duration } = this.props;
    const { getFieldDecorator } = form;
    const { variables: { values }, data } = service;
    const { getServiceResponseTimeTrend, getServiceTPSTrend,
      getServiceSLATrend, getServiceTopology } = data;
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
                contentHeight={46}
              >
                <MiniArea
                  color="#975FE4"
                  data={axis(duration, getServiceTPSTrend.trendList)}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg Response Time"
                total={`${this.avg(getServiceResponseTimeTrend.trendList)} ms`}
                contentHeight={46}
              >
                <MiniArea
                  data={axis(duration, getServiceResponseTimeTrend.trendList)}
                />
              </ChartCard>
            </Col>
            <Col xs={24} sm={24} md={24} lg={8} xl={8} style={{ marginTop: 24 }}>
              <ChartCard
                title="Avg SLA"
                total={`${this.avg(getServiceSLATrend.trendList) / 100} %`}
              >
                <MiniBar
                  animate={false}
                  height={46}
                  data={axis(duration, getServiceSLATrend.trendList,
                    ({ x, y }) => ({ x, y: y / 100 }))}
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
