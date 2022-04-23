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

## Settings

Settings provide language, server time zone, and auto-fresh options. These settings are stored in the browser's local storage. Unless you clear them manually, those will not change. 

## FAQ

### Login and Authentication

SkyWalking doesn't provide login and authentication as usual for years. If you need, a lot of Gateway solutions have
provides well-established solutions, such as the Nginx ecosystem.
