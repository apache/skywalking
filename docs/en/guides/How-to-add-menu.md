# How to add a new root menu or sub-menu to booster UI

If you would like to add a new root menu or sub-menu, you should add data to `src/router/data/xx` and add translation contents for the title to `src/locales/lang/xx` in [booster UI](https://github.com/apache/skywalking-booster-ui).

## Add a sub-menu to a root menu

Add configurations to `src/router/data/xx` of the children field, and the configurations should be like this.
```ts
{
  path: "/linux",
  name: "Linux",
  meta: {
    title: "linux",
    layer: "OS_LINUX",
  },
},
```

If there are Tabs widgets in your dashboards, you should add other configurations to the children field of `src/router/data/xx`.
```ts
{
  path: "/linux/tab/:activeTabIndex",
  name: "LinuxActiveTabIndex",
  meta: {
    title: "linux",
    notShow: true,
    layer: "OS_LINUX",
  },
},
```

## Add a root menu to side bar

1. Create a new file called `xxx.ts` in `src/router/data`.
2. Add configurations to the `xxx.ts`, configurations should be like this.
```ts
export default [
  {
    path: "",
    name: "Infrastructure",
    meta: {
      title: "infrastructure",
      icon: "scatter_plot",
      hasGroup: true,
    },
    redirect: "/linux",
    children: [
      {
        path: "/linux",
        name: "Linux",
        meta: {
          title: "linux",
          layer: "OS_LINUX",
        },
      },
    ],
  },
];
```
3. import configurations in `src/router/data/index.ts`.
```ts
import name from "./xxx";
```
