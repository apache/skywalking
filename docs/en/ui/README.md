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

## What Horizon UI provides

Horizon UI is the official web UI and the visualization layer of
SkyWalking. It is a separate sub-project with its own release cadence
and configuration; OAP only persists the dashboards that operators save.
At a high level Horizon UI covers:

* **Setup** — container image, `horizon.yaml`, OAP connection, file
  layout, debug logging.
* **Access control** — local / LDAP / break-glass auth backends, RBAC
  roles and verbs, audit log, admin-page gating.
* **Customization** — sidebar menu structure, layer dashboard templates,
  overview templates, adding a new layer.
* **Components** — overview widgets, dashboard widgets, chart types.
* **Operate** — cluster status & metadata, inspect.

Each area lives in the
[Horizon UI documentation](https://skywalking.apache.org/docs/skywalking-horizon-ui/next/readme/).

## Bundled UI templates

The OAP backend does **not** seed dashboard JSONs at boot anymore.
Horizon UI owns and ships the entire bundled set in its own image: the
default dashboard configurations for every supported layer, several
cross-cutting overview and 3D infrastructure-map setups, the sidebar
menu, and i18n translations. Operators customize these in the UI's admin
pages; see the
[Horizon UI documentation](https://skywalking.apache.org/docs/skywalking-horizon-ui/next/readme/).

## Editing dashboards from the UI

Horizon UI's dashboard editor drives the admin REST endpoints from the
browser. The write surface is gated behind the admin host's
security posture; see the
[admin-server security notice](../setup/backend/admin-api/readme.md#-security-notice).
