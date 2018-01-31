import mockjs from 'mockjs';
import { getRule, postRule } from './mock/rule';
import { imgMap } from './mock/utils';
import { getNotices } from './mock/notices';
import { delay } from 'roadhog-api-doc';
import { getDashboard } from './mock/dashboard';
import { getTopology } from './mock/topology';
import { getAllApplication, getApplication } from './mock/application';
import { searchServer, getServer } from './mock/server';
import { searchService, getService } from './mock/service';
import { getAlarm } from './mock/alarm';
import { getAllApplication as getAllApplicationForTrace, getTrace, getSpans } from './mock/trace'

// 是否禁用代理
const noProxy = process.env.NO_PROXY === 'true';

// 代码中会兼容本地 service mock 以及部署站点的静态数据
const proxy = {
  // 支持值为 Object 和 Array
  'POST /api/dashboard': getDashboard,
  'POST /api/topology': getTopology,
  'POST /api/application/options': getAllApplication,
  'POST /api/application': getApplication,
  'POST /api/server/search': searchServer,
  'POST /api/server': getServer,
  'POST /api/service/search': searchService,
  'POST /api/service': getService,
  'POST /api/alarm': getAlarm,
  'POST /api/trace/options': getAllApplicationForTrace,
  'POST /api/trace': getTrace,
  'POST /api/spans': getSpans,
};

export default noProxy ? {} : delay(proxy, 1000);
