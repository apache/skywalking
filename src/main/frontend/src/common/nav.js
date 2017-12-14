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
    ],
  },
];
