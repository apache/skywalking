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
        icon: 'topology',
        path: 'topology',
        component: dynamicWrapper(app, ['topology'], () => import('../routes/Topology/Topology')),
      },
      {
        name: 'Application',
        icon: 'application',
        path: 'application',
        component: dynamicWrapper(app, ['application'], () => import('../routes/Application/Application')),
      },
      {
        name: 'Server',
        icon: 'server',
        path: 'server',
        component: dynamicWrapper(app, ['server'], () => import('../routes/Server/Server')),
      },
      {
        name: 'Service',
        icon: 'service',
        path: 'service',
        component: dynamicWrapper(app, ['service'], () => import('../routes/Service/Service')),
      },
      {
        name: 'Trace',
        icon: 'trace',
        path: 'trace',
        component: dynamicWrapper(app, ['trace'], () => import('../routes/Trace/Trace')),
      },
      {
        name: 'Alert',
        icon: 'alert',
        path: 'alert',
        component: dynamicWrapper(app, ['alert'], () => import('../routes/Alert/Alert')),
      },
    ],
  },
];
