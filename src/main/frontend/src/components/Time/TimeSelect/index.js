import React, { PureComponent } from 'react';
import { Button, Row, Col, Divider, Form, DatePicker, Select } from 'antd';
import moment from 'moment';
import styles from './index.less';

const { Option } = Select;
const FormItem = Form.Item;
const { RangePicker } = DatePicker;

@Form.create({
  mapPropsToFields(props) {
    if (!props.duration) return null;
    const result = {
      step: Form.createFormField({
        value: props.duration.step,
      }),
    };
    if (props.duration.label) {
      return result;
    }
    result['range-time-picker'] = Form.createFormField({
      value: [props.duration.from(), props.duration.to()],
    });
    return result;
  },
})
class TimeSelect extends PureComponent {
  constructor(props) {
    super(props);

    const now = {
      to() {
        return moment();
      },
    };
    this.shortcuts = [
      { ...now,
        from() {
          return moment().subtract('minutes', 5);
        },
        label: 'Last 5 minutes',
      },
      { ...now,
        from() {
          return moment().subtract('minutes', 15);
        },
        label: 'Last 15 minutes',
      },
      { ...now,
        from() {
          return moment().subtract('minutes', 30);
        },
        label: 'Last 30 minutes',
      },
      { ...now,
        from() {
          return moment().subtract('hours', 1);
        },
        label: 'Last 1 hour',
      },
      { ...now,
        from() {
          return moment().subtract('hours', 3);
        },
        label: 'Last 3 hours',
      },
      { ...now,
        from() {
          return moment().subtract('hours', 6);
        },
        label: 'Last 6 hours',
      },
      { ...now,
        from() {
          return moment().subtract('hours', 12);
        },
        label: 'Last 12 hours',
      },
      { ...now,
        from() {
          return moment().subtract('hours', 24);
        },
        label: 'Last 24 hours',
      },
    ];
    this.shortcutsDays = [
      { ...now,
        from() {
          return moment().subtract('days', 2);
        },
        label: 'Last 2 days',
      },
      { ...now,
        from() {
          return moment().subtract('days', 7);
        },
        label: 'Last 7 days',
      },
      { ...now,
        from() {
          return moment().subtract('days', 14);
        },
        label: 'Last 14 days',
      },
    ];
  }
  componentDidMount() {
    const { onSelected } = this.props;
    onSelected(this.shortcuts[0]);
  }
  disabledDate = (current) => {
    return current && current.valueOf() >= Date.now();
  }
  handleSubmit = (e) => {
    e.preventDefault();

    const { form } = this.props;

    form.validateFields((err, fieldsValue) => {
      if (err) return;
      const duration = {};
      for (const key of Object.keys(fieldsValue)) {
        if (fieldsValue[key]) {
          if (key === 'range-time-picker') {
            duration.from = () => fieldsValue[key][0];
            duration.to = () => fieldsValue[key][1];
          } else {
            duration[key] = fieldsValue[key];
          }
        }
      }
      if (duration.from && duration.to) {
        this.select({ ...duration, label: null });
      } else {
        this.select(duration);
      }
    });
  }
  select = (newDuration) => {
    const { onSelected, duration } = this.props;
    onSelected({ ...duration, ...newDuration });
  }
  render() {
    const { isShow, form } = this.props;
    if (!isShow) {
      return null;
    }
    const formItemLayout = {
      labelCol: {
        xs: { span: 24 },
        sm: { span: 7 },
      },
      wrapperCol: {
        xs: { span: 24 },
        sm: { span: 12 },
        md: { span: 10 },
      },
    };
    const { getFieldDecorator } = form;
    const content = (
      <Row type="flex" justify="end">
        <Col xs={24} sm={24} md={24} lg={15} xl={14}>
          <Form
            onSubmit={this.handleSubmit}
            hideRequiredMark
          >
            <FormItem
              {...formItemLayout}
              label="Time Range"
            >
              {getFieldDecorator('range-time-picker')(
                <RangePicker showTime disabledDate={this.disabledDate} format="YYYY-MM-DD HH:mm:ss" />
              )}
            </FormItem>
            <FormItem
              {...formItemLayout}
              label="Reloading every "
            >
              {getFieldDecorator('step')(
                <Select style={{ width: 170 }}>
                  <Option value="0">off</Option>
                  <Option value="5000">5s</Option>
                  <Option value="10000">10s</Option>
                  <Option value="30000">30s</Option>
                </Select>
              )}
            </FormItem>
            <FormItem>
              <Button
                type="primary"
                htmlType="submit"
              >
                Apply
              </Button>
            </FormItem>
          </Form>
        </Col>
        <Col xs={0} sm={0} md={0} lg={0} xl={1}><Divider type="vertical" style={{ height: 200 }} /></Col>
        <Col xs={24} sm={24} md={4} lg={4} xl={4}>
          <ul className={styles.list}>
            {this.shortcutsDays.map(d => (
              <li key={d.label}>
                <a onClick={this.select.bind(this, d)}>
                  {d.label}
                </a>
              </li>))
            }
          </ul>
        </Col>
        <Col xs={24} sm={24} md={4} lg={4} xl={4}>
          <ul className={styles.list}>
            {this.shortcuts.map(d => (
              <li key={d.label}>
                <a onClick={this.select.bind(this, d)}>
                  {d.label}
                </a>
              </li>))
            }
          </ul>
        </Col>
      </Row>
    );
    return (
      <div className="antd-pro-page-header-pageHeader">
        <div className="antd-pro-page-header-detail">
          <div className="antd-pro-page-header-main">
            <div className="antd-pro-page-header-row">
              <div className="antd-pro-page-header-content">
                {content}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
export default TimeSelect;
