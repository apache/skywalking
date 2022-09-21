# How to add a new root menu or sub-menu to booster UI

If you would like to add a new root menu or sub-menu, you should add data to `src/router/data/xx` and add translation contents for the title to `src/locales/lang/xx` in [booster UI](https://github.com/apache/skywalking-booster-ui).

1. Create a new file called `xxx.ts` in `src/router/data`.
2. Add configurations to the `xxx.ts`, configurations should be like this.
```ts
export default [
  {
    // Add `Infrastructure` menu
    path: "",
    name: "Infrastructure",
    meta: {
      title: "infrastructure",
      icon: "scatter_plot",
      hasGroup: true,
    },
    redirect: "/linux",
    children: [
      // Add a sub menu of the `Infrastructure`
      {
        path: "/linux",
        name: "Linux",
        meta: {
          title: "linux",
          layer: "OS_LINUX",
        },
      },
      // If there are Tabs widgets in your dashboards, add following extra configuration to provide static links to the specific tab.
      {
        path: "/linux/tab/:activeTabIndex",
        name: "LinuxActiveTabIndex",
        meta: {
          title: "linux",
          notShow: true,
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
