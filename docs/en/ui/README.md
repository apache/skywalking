# UI Customization

The OAP backend in this repository ships the **data plane** — receivers,
analyzers, metric definitions, entity model, query surfaces. It does **not**
ship dashboards or marketplace visualizations. Those live in
[apache/skywalking-horizon-ui](https://github.com/apache/skywalking-horizon-ui),
the official web UI, which releases independently of the OAP backend.

As of 11.0.0 the boot-time `UITemplateInitializer` / `UIMenuInitializer`
have been removed and OAP no longer seeds any dashboard JSONs or menu
items on startup. The dashboard library, the sidebar menu, the
marketplace layout, i18n strings — every layer of *visualization* —
ships from Horizon UI.

## Customization split

| Concern | Where it lives |
|---|---|
| Custom metrics (OAL / MAL / LAL rules) | OAP backend (this repo) — see Customization docs |
| New telemetry receivers / analyzers | OAP backend (this repo) |
| Dashboard layouts, widgets, marketplace UI | [Horizon UI](https://github.com/apache/skywalking-horizon-ui) |
| Sidebar menu structure, i18n keys, descriptions | [Horizon UI](https://github.com/apache/skywalking-horizon-ui) (see [i18n guide](../guides/i18n.md)) |
| Persisting operator-saved dashboard templates | OAP storage, written via the admin REST surface |
| Editing dashboards at runtime | OAP admin host — `POST/PUT /ui-management/templates` (see [UI Management API](../setup/backend/admin-api/ui-management.md)) |

The OAP-side write contract is preserved by `UITemplateManagementService`
+ the storage DAO, exposed as REST. Horizon UI's dashboard editor drives
those same endpoints.

## Pushing custom dashboard templates into OAP

The OAP backend does **not** seed dashboard JSONs at boot anymore.
Operators who want to ship custom dashboards push them through the admin
REST surface at runtime:

```bash
curl -s -X POST "http://oap:17128/ui-management/templates" \
  -H 'Content-Type: application/json' \
  -d '{"configuration":"{\"name\":\"my-dashboard\",...}"}'
```

The response body is a `TemplateChangeStatus` with the server-generated
template UUID. See [UI Management API](../setup/backend/admin-api/ui-management.md)
for the full endpoint reference.

## Editing dashboards from the UI

Horizon UI's dashboard editor drives the admin REST endpoints from the
browser — no `SW_ENABLE_UPDATE_UI_TEMPLATE` flag is needed (the flag has
been removed). The write surface is gated behind the admin host's
security posture; see the
[admin-server security notice](../setup/backend/admin-api/readme.md#-security-notice).

## Contributing dashboards upstream

New layer dashboards, widget improvements, marketplace polish, and i18n
keys all go to the
[Horizon UI repository](https://github.com/apache/skywalking-horizon-ui/blob/main/CONTRIBUTING.md).
Backend support for the metrics behind the dashboards (new OAL / MAL /
LAL rules, new receivers, new entity types) goes to this repo.
