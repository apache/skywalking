import moment from 'moment';
import { query as queryService } from '../services/graphql';

function createTimeMeasure(measureType, step, format, displayFormat = format) {
  return {
    measureType, step, format, displayFormat,
  };
}

function getMeasureList() {
  return [createTimeMeasure('months', 'MONTH', 'YYYY-MM'), createTimeMeasure('days', 'DAY', 'YYYY-MM-DD'),
    createTimeMeasure('hours', 'HOUR', 'YYYY-MM-DD HH', 'YYYY-MM-DD HH:00:00'), createTimeMeasure('minutes', 'MINUTE', 'YYYY-MM-DD HHmm', 'HH:mm:00'),
    createTimeMeasure('seconds', 'SECOND', 'YYYY-MM-DD HHmmss', 'HH:mm:ss')];
}

export function fixedZero(val) {
  return val * 1 < 10 ? `0${val}` : val;
}

export function getTimeDistance(type) {
  const now = new Date();
  const oneDay = 1000 * 60 * 60 * 24;

  if (type === 'today') {
    now.setHours(0);
    now.setMinutes(0);
    now.setSeconds(0);
    return [moment(now), moment(now.getTime() + (oneDay - 1000))];
  }

  if (type === 'week') {
    let day = now.getDay();
    now.setHours(0);
    now.setMinutes(0);
    now.setSeconds(0);

    if (day === 0) {
      day = 6;
    } else {
      day -= 1;
    }

    const beginTime = now.getTime() - (day * oneDay);

    return [moment(beginTime), moment(beginTime + ((7 * oneDay) - 1000))];
  }

  if (type === 'month') {
    const year = now.getFullYear();
    const month = now.getMonth();
    const nextDate = moment(now).add(1, 'months');
    const nextYear = nextDate.year();
    const nextMonth = nextDate.month();

    return [moment(`${year}-${fixedZero(month + 1)}-01 00:00:00`), moment(moment(`${nextYear}-${fixedZero(nextMonth + 1)}-01 00:00:00`).valueOf() - 1000)];
  }

  if (type === 'year') {
    const year = now.getFullYear();

    return [moment(`${year}-01-01 00:00:00`), moment(`${year}-12-31 23:59:59`)];
  }
}

export function getPlainNode(nodeList, parentPath = '') {
  const arr = [];
  nodeList.forEach((node) => {
    const item = node;
    item.path = `${parentPath}/${item.path || ''}`.replace(/\/+/g, '/');
    item.exact = true;
    if (item.children && !item.component) {
      arr.push(...getPlainNode(item.children, item.path));
    } else {
      if (item.children && item.component) {
        item.exact = false;
      }
      arr.push(item);
    }
  });
  return arr;
}

export function digitUppercase(n) {
  const fraction = ['角', '分'];
  const digit = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'];
  const unit = [
    ['元', '万', '亿'],
    ['', '拾', '佰', '仟'],
  ];
  let num = Math.abs(n);
  let s = '';
  fraction.forEach((item, index) => {
    s += (digit[Math.floor(num * 10 * (10 ** index)) % 10] + item).replace(/零./, '');
  });
  s = s || '整';
  num = Math.floor(num);
  for (let i = 0; i < unit[0].length && num > 0; i += 1) {
    let p = '';
    for (let j = 0; j < unit[1].length && num > 0; j += 1) {
      p = digit[num % 10] + unit[1][j] + p;
      num = Math.floor(num / 10);
    }
    s = p.replace(/(零.)*零$/, '').replace(/^$/, '零') + unit[0][i] + s;
  }

  return s.replace(/(零.)*零元/, '元').replace(/(零.)+/g, '零').replace(/^整$/, '零元整');
}

export function timeRange({ display }) {
  return display.range;
}

export function generateDuration({ from, to }) {
  const start = from();
  const end = to();
  const { measureType, step, format, displayFormat } = getMeasureList()
    .find(measure => (end.diff(start, measure.measureType) > 1));
  return {
    input: {
      start: start.format(format),
      end: end.format(format),
      step,
    },
    display: {
      range: Array.from({ length: end.diff(start, measureType) + 1 },
        (v, i) => start.clone().add(i, measureType).format(displayFormat)),
    },
  };
}

export function generateModal({ namespace, dataQuery, optionsQuery, state = {},
  effects = {}, reducers = {} }) {
  return {
    namespace,
    state: {
      variables: {
        values: {},
        labels: {},
        options: {},
      },
      data: state,
    },
    effects: {
      *initOptions({ payload }, { call, put }) {
        const { variables, reducer = undefined } = payload;
        const response = yield call(queryService, `${namespace}/options`, { variables, query: optionsQuery });
        if (reducer) {
          yield put({
            type: reducer,
            payload: response.data,
          });
        } else {
          yield put({
            type: 'saveOptions',
            payload: response.data,
          });
        }
      },
      *fetchData({ payload }, { call, put }) {
        const { variables, reducer = undefined } = payload;
        const response = yield call(queryService, namespace, { variables, query: dataQuery });
        if (reducer) {
          yield put({
            type: reducer,
            payload: response.data,
          });
        } else {
          yield put({
            type: 'saveData',
            payload: response.data,
          });
        }
      },
      ...effects,
    },
    reducers: {
      saveOptions(preState, { payload: allOptions }) {
        const { variables } = preState;
        const { values, labels, options } = variables;
        const amendOptions = {};
        const defaultValues = {};
        const defaultLabels = {};
        Object.keys(allOptions).forEach((_) => {
          const thisOptions = allOptions[_];
          if (!values[_] && thisOptions.length > 0) {
            defaultValues[_] = thisOptions[0].key;
            defaultLabels[_] = thisOptions[0].label;
          }
          const key = values[_];
          if (!thisOptions.find(o => o.key === key)) {
            amendOptions[_] = [...thisOptions, { key, label: labels[_] }];
          }
        });
        return {
          ...preState,
          variables: {
            ...variables,
            options: {
              ...options,
              ...allOptions,
              ...amendOptions,
            },
            values: {
              ...values,
              ...defaultValues,
            },
            labels: {
              ...labels,
              ...defaultLabels,
            },
          },
        };
      },
      save(preState, { payload: { variables: { values = {}, options = {}, labels = {} },
        data = {} } }) {
        const { variables: { values: preValues, options: preOptions, labels: preLabels },
          data: preData } = preState;
        return {
          variables: {
            values: {
              ...preValues,
              ...values,
            },
            options: {
              ...preOptions,
              ...options,
            },
            labels: {
              ...preLabels,
              labels,
            },
          },
          data: {
            ...preData,
            ...data,
          },
        };
      },
      saveData(preState, { payload }) {
        const { data } = preState;
        return {
          ...preState,
          data: {
            ...data,
            ...payload,
          },
        };
      },
      saveVariables(preState, { payload: { values: variableValues, labels = {} } }) {
        const { variables: preVariables } = preState;
        const { values: preValues, lables: preLabels } = preVariables;
        return {
          ...preState,
          variables: {
            ...preVariables,
            values: {
              ...preValues,
              ...variableValues,
            },
            labels: {
              ...preLabels,
              ...labels,
            },
          },
        };
      },
      ...reducers,
    },
  };
}
