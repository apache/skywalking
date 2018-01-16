import mockjs from 'mockjs';
import { getRule, postRule } from './mock/rule';
import { imgMap } from './mock/utils';
import { getNotices } from './mock/notices';
import { delay } from 'roadhog-api-doc';
import { getDashboard } from './mock/dashboard';
import { getApplication } from './mock/application';
import { getServer } from './mock/server';
import { getService } from './mock/service';
import { getAlarm } from './mock/alarm';
import { getTrace } from './mock/trace'

// 是否禁用代理
const noProxy = process.env.NO_PROXY === 'true';

// 代码中会兼容本地 service mock 以及部署站点的静态数据
const proxy = {
  // 支持值为 Object 和 Array
  'POST /api/dashboard': getDashboard,
  'POST /api/application': getApplication,
  'POST /api/server': getServer,
  'POST /api/service': getService,
  'POST /api/alert': getAlarm,
  'POST /api/trace': getTrace,
};

export default noProxy ? {} : delay(proxy, 1000);
