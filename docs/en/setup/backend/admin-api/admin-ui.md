# Admin UI

The Admin API is a set of HTTP endpoints (Runtime Rule Hot-Update, DSL
Debug, Status, Inspect) — operators drive them with `curl`, `swctl`, or a
front-end of their choice. SkyWalking does not ship an official admin UI.

## Vantage Studio (community)

[**Vantage Studio**](https://github.com/SkyAPM/vantage-studio) is a
community-built web UI that consumes SkyWalking's Admin API surface. It
provides a visual driver for the same endpoints documented in this
section.

* Project: <https://github.com/SkyAPM/vantage-studio>
* Author: [Sheng Wu](https://github.com/wu-sheng)
* Status: Community project under the SkyAPM organization — not an Apache
  SkyWalking release artifact. License, release cadence, and support
  follow that project's repository.

Operators looking for a UI in front of `/runtime/rule/*`,
`/dsl-debugging/*`, `/status/*`, and `/inspect/*` can adopt it the same
way they would any third-party tool — install separately, point it at the
admin port (default `17128`) behind the same gateway / authenticating
reverse proxy the [admin-server security
notice](readme.md#security-notice) requires.
