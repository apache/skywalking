# Introduction to UI

The SkyWalking official UI provides the default and powerful visualization capabilities for SkyWalking to observe
full-stack application.

<img src="https://skywalking.apache.org/ui-doc/9.0.0/home.png"/>

The left side menu lists all available supported stack, with default dashboards.

Follow `Official Dashboards` menu explores all default dashboards about how to monitor different tech stacks.

## Custom Dashboard

Besides, official dashboards, **Dashboards** provides customization to end users to add new tabs/pages/widgets, and
flexibility to re-config the dashboard on your own preference.

The dashboard has two key attributes, **Layer** and **Entity Type**. Learn these two concepts first before you begin any
customization. Also, trace, metrics, log analysis are relative to OAL, MAL, and LAL engines in SkyWalking kernel. You
should learn them first too.

Service and All entity type dashboard could be set as root(`set this to root`), which mean this dashboard would be used
as the entrance of its layer. If you have multiple root dashboards, UI could choose one randomly(Don't recommend doing
so).

**Notice, dashboard editable is disabled on release, set system env(**SW_ENABLE_UPDATE_UI_TEMPLATE=true**) to activate
them.** Before you save the edited dashboard, it is just stored in memory, closing tab would LOSE the change
permanently.

## Settings

Settings provide language, server time zone, and auto-fresh option. These settings are stored in browser local
storage. Unless you clear them manually, those would not change. 

## FAQ

### Login and Authentication

SkyWalking doesn't provide login and authentication as usual for years. If you need, a lot of Gateway solutions have
provides well-established solutions, such as Nginx ecosystem.