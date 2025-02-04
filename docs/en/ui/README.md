# Introduction to UI

The SkyWalking official UI provides the default and powerful visualization capabilities for SkyWalking to observe full-stack applications.

<img src="https://skywalking.apache.org/ui-doc/10.2.0/home.png"/>

The left side menu lists all available supported stacks with default dashboards.

Follow the `Official Dashboards` menu to explore all default dashboards on their ways to monitor different tech stacks.

## Sidebar Menu and Marketplace

All available feature menu items are only listed in the marketplace(since 9.6.0). They are only visible on the Sidebar Menu when there are relative services
being observed by various supported observation agents, such as installed language agents, service mesh platform, OTEL integration.

The menu items defined in `ui-initialized-templates/menu.yaml` are the universal marketplace for all default-supported integration.
The menu definition supports one and two levels items. The leaf menu item should have the `layer` for navigation.

```yaml
menus:
  - name: GeneralService
    icon: general_service
    menus:
      - name: Services
        layer: GENERAL
      - name: VisualDatabase
        layer: VIRTUAL_DATABASE
      - name: VisualCache
        layer: VIRTUAL_CACHE
      - name: VisualMQ
        layer: VIRTUAL_MQ
  ....
- name: SelfObservability
  icon: self_observability
  menus:
    - name: SkyWalkingServer
      layer: SO11Y_OAP
    - name: Satellite
      layer: SO11Y_SATELLITE
```


The menu items would automatically pop up on the left after short period of time that at least one service was observed.
For more details, please refer to the "uiMenuRefreshInterval" configuration item in the [backend settings](../setup/backend/configuration-vocabulary.md)

## Custom Dashboard

Besides official dashboards, **Dashboards** provide customization capabilities to end-users to add new tabs/pages/widgets, and
flexibility to re-config the dashboard on your own preference.

The dashboard has two key attributes, **Layer** and **Entity Type**. Learn these two concepts first before you begin any
customization. Also, trace, metrics, and log analysis are relative to OAL, MAL, and LAL engines in the SkyWalking kernel. It would help if you
learned them first, too.

Service and All entity type dashboard could be set as root(`set this to root`), which means this dashboard would be used
as the entrance of its Layer. If you have multiple root dashboards, UI will choose one randomly (We don't recommend doing
so).

**Notice, dashboard editable is disabled on release; set system env(**SW_ENABLE_UPDATE_UI_TEMPLATE=true**) to activate
them.** Before you save the edited dashboard, it is just stored in memory. Closing a tab would **LOSE** the change permanently.

A new dashboard should be added through `New Dashboard` in the `Dashboards` menu.
Meanwhile, there are two ways to edit an existing dashboard.
1. `Dashboard List` in the `Dashboard` menu provides edit/delete/set-as-root features to manage existing dashboards.
2. In every dashboard page, click the right top `V` toggle, and turn to `E`(representing **Edit**) mode.

## Widget

A dashboard consists of various widget. In the `Edit` mode, widgets could be added/moved/removed/edit according to the Layer.(Every widget declares its suitable layer.)

The widget provides the ability to visualize the metrics, generated through [OAL](../concepts-and-designs/mal.md), [MAL](../concepts-and-designs/mal.md), or [LAL](../concepts-and-designs/lal.md) scripts.

<img src="https://skywalking.apache.org/ui-doc/10.2.0/customize-dashboard-metrics-20250202.png" />

### Metric Query Expression
Metric Query Expression provides the ability to query metrics from the backend. 
The query expression could be a simple metric name, or a complex expression with multiple functions.
Read [Metrics Query Expression(MQE) Syntax doc](../api/metrics-query-expression.md) to learn more about the syntax.

### Graph styles

Setup font size, unit, value mappings etc. to provide better visualization.

### Widget options

<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-options.png" />

Define the following properties of the widget:
1. **Name**: The name of the widget, which used to [associate with other widget](#association-options) in the dashboard.
2. **Title**: The title name of the widget.
3. **Tooltip Content**: Additional explanation of the widget.

### Association Options

<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-association.png" />

Widget provides the ability to associate with other widgets to show axis pointer with tips for the same time point, in order to help users to understand
the connectivity among metrics.

### Widget Static Link
On the right top of every widget on the dashboard, there is a `Generate Link` option, which could generate a static
link to represent this widget.
By using this link, users could share this widget, or integrate it into any 3rd party iFrame to build a
network operations center(NOC) dashboard on the wall easily.
About this link, there are several customizable options
1. `Lock Query Duration`. Set the query duration manually. It is OFF by default.
2. `Auto Fresh` option is ON with 6s query period and last 30 mins time range. Query period and range are customizable.

## Settings

Settings provide language, server time zone, and auto-fresh options. These settings are stored in the browser's local storage. Unless you clear them manually, those will not change.

## FAQ

### Login and Authentication

SkyWalking doesn't provide login and authentication as usual for years. If you need, a lot of Gateway solutions have
provides well-established solutions, such as the Nginx ecosystem.
