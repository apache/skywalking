# Introduction to UI

The SkyWalking official UI provides the default and powerful visualization capabilities for SkyWalking to observe full-stack applications.

<img src="https://skywalking.apache.org/ui-doc/9.0.0/home.png"/>

The left side menu lists all available supported stacks with default dashboards.

Follow the `Official Dashboards` menu to explore all default dashboards on their ways to monitor different tech stacks.

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

### Widget

The widget provide the ability to visualize the metrics, which can generate from the [OAL](../concepts-and-designs/mal.md), [MAL](../concepts-and-designs/mal.md), or [LAL](../concepts-and-designs/lal.md).

<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-configuration.png" />

#### Metrics

To display one or more metrics in a graph, the following information is required:
1. **Name**: The name of metrics.
2. **Data Type**: How to read the metrics data.
3. **Visualization**: How to visualize the metrics data, which needs to cooperate with the metrics data type.
4. **Unit**: The unit of the metrics data.
5. **Calculation**: After the metrics are read, the data can be calculated and display. The following types are supported. 

##### Calculations

|Label|Calculation|
|----|----|
|Percentage|Value / 100|
|Apdex|Value / 10000|
|Average|Sum of values / Count of values|
|Percentage + Avg-preview|Sum of values / Count of values / 100|
|Apdex + Avg-preview|Sum of values / Count of values / 10000|
|Byte to KB|Value / 1024|
|Byte to MB|Value / 1024 / 1024|
|Byte to GB|Value / 1024 / 1024 / 1024|
|Seconds to YYYY-MM-DD HH:mm:ss|dayjs(value * 1000).format("YYYY-MM-DD HH:mm:ss")|
|Milliseconds to YYYY-MM-DD HH:mm:ss|dayjs(value).format("YYYY-MM-DD HH:mm:ss")|
|Precision|Value.toFixed(2)|
|Milliseconds to seconds|Value / 1000|
|Seconds to days|Value / 86400|

##### Common Graphs

|Metrics Data Type|Visualization|Demo|
|----|-------------|----|
|read all values in the duration|Line|<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-line.png" />|
|get sorted top N values|Top List|<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-topn.png" />|
|read all values of labels in the duration|Table|<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-tables.png" />|
|read all values in the duration|Area|<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-area.png" />|
|read all values in the duration|Service/Instance/Endpoint List|<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-entity-table.png" />|

#### Graph styles

Defines the display style of the graph.

#### Widget options

<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-options.png" />

Define the following properties of the widget:
1. **Name**: The name of the widget, which used to cooperate with other widget in the dashboard.
2. **Title**: The title name of the widget. 
3. **Tooltip Content**: Additional explanation of the widget. 

#### Association Options

<img src="https://skywalking.apache.org/screenshots/9.2.0/customize-dashboard-metrics-20220817-association.png" />

Widget provide the ability to link with other widget to display mark lines. When the current widget data point is selected,
the data point of the associated widget at the same moment would be displayed together.

## Settings

Settings provide language, server time zone, and auto-fresh options. These settings are stored in the browser's local storage. Unless you clear them manually, those will not change. 

## FAQ

### Login and Authentication

SkyWalking doesn't provide login and authentication as usual for years. If you need, a lot of Gateway solutions have
provides well-established solutions, such as the Nginx ecosystem.
