import dynamic from 'dva/dynamic';

// wrapper of dynamic
const dynamicWrapper = (app, models, component) => dynamic({
  app,
  models: () => models.map(m => import(`../models/${m}.js`)),
  component,
});

// nav data
export const getNavData = app => [
  {
    component: dynamicWrapper(app, ['user'], () => import('../layouts/BasicLayout')),
    layout: 'BasicLayout',
    name: 'Main', // for breadcrumb
    path: '/',
    children: [
      {
        name: 'Dashboard',
        icon: 'dashboard',
        path: 'dashboard',
        component: dynamicWrapper(app, ['dashboard'], () => import('../routes/Dashboard/Dashboard')),
      },
      {
        name: 'Topology',
        icon: 'iconfont icon-network',
        path: 'topology',
        component: dynamicWrapper(app, ['topology'], () => import('../routes/Topology/Topology')),
      },
      {
        name: 'Application',
        icon: 'appstore',
        path: 'application',
        component: dynamicWrapper(app, ['application'], () => import('../routes/Application/Application')),
      },
      {
        name: 'Server',
        icon: 'iconfont icon-server',
        path: 'server',
        component: dynamicWrapper(app, ['server'], () => import('../routes/Server/Server')),
      },
      {
        name: 'Service',
        icon: 'api',
        path: 'service',
        component: dynamicWrapper(app, ['service'], () => import('../routes/Service/Service')),
      },
      {
        name: 'Trace',
        icon: 'iconfont icon-icon-traces-search',
        path: 'trace',
        component: dynamicWrapper(app, ['trace'], () => import('../routes/Trace/Trace')),
      },
      {
        name: 'Alarm',
        icon: 'iconfont icon-ALERT',
        path: 'alarm',
        component: dynamicWrapper(app, ['alarm'], () => import('../routes/Alarm/Alarm')),
      },
    ],
  },
];
