import { isUrl } from '../utils/utils';

const menuData = [{
  name: 'Dashboard',
  icon: 'dashboard',
  path: 'dashboard',
}, {
  name: 'Topology',
  icon: 'iconfont icon-network',
  path: 'topology',
}, {
  name: 'Application',
  icon: 'appstore',
  path: 'application',
}, {
  name: 'Server',
  icon: 'iconfont icon-server',
  path: 'server',
}, {
  name: 'Service',
  icon: 'api',
  path: 'service',
}, {
  name: 'Trace',
  icon: 'iconfont icon-icon-traces-search',
  path: 'trace',
}, {
  name: 'Alarm',
  icon: 'iconfont icon-ALERT',
  path: 'alarm',
}];

function formatter(data, parentPath = '', parentAuthority) {
  return data.map((item) => {
    let { path } = item;
    if (!isUrl(path)) {
      path = parentPath + item.path;
    }
    const result = {
      ...item,
      path,
      authority: item.authority || parentAuthority,
    };
    if (item.children) {
      result.children = formatter(item.children, `${parentPath}${item.path}/`, item.authority);
    }
    return result;
  });
}

export const getMenuData = () => formatter(menuData);
