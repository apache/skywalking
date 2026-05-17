# Admin UI

The official SkyWalking web UI is
[**Horizon UI**](https://github.com/apache/skywalking-horizon-ui), a
sub-project of Apache SkyWalking. It consumes the OAP's public query
surface (GraphQL / MQE on `core.restPort`, default `12800`) and the
admin host (`/ui-management/*`, `/status/*`, `/inspect/*`,
`/dsl-debugging/*`, `/runtime/rule/*` on the admin port, default
`17128`).

Horizon UI **releases independently** of the OAP backend. There is no 1:1
mapping between OAP versions and Horizon UI versions — pin the UI image
tag explicitly and upgrade the UI on the cadence that fits your operator
workflow. The cross-version compatibility surface is the GraphQL schema
and the admin REST API, both of which are documented and stable across
backward-compatible OAP releases.

## Container image

Images are published on the GitHub Container Registry:

* Registry: <https://github.com/apache/skywalking-horizon-ui/pkgs/container/skywalking-horizon-ui>
* Pull: `docker pull ghcr.io/apache/skywalking-horizon-ui:latest`
* Pin: replace `latest` with a Horizon UI release tag or commit SHA for
  reproducibility.

## Running with Docker

```shell
docker run --name skywalking-ui --restart always -d \
  -p 8080:8080 \
  -e SW_OAP_ADDRESS=http://oap:12800 \
  -e SW_ADMIN_ADDRESS=http://oap:17128 \
  -e SW_ZIPKIN_ADDRESS=http://oap:9412 \
  ghcr.io/apache/skywalking-horizon-ui:latest
```

| Env var | Purpose |
|---|---|
| `SW_OAP_ADDRESS` | OAP public GraphQL / REST host — read-side queries. |
| `SW_ADMIN_ADDRESS` | OAP admin host — UI management, status, inspect, runtime-rule, DSL debug. |
| `SW_ZIPKIN_ADDRESS` | OAP Zipkin v2 query endpoint. |

See [UI setup](../ui-setup.md) for the end-to-end deployment procedure
and the security guidance in the
[admin-server security notice](readme.md#-security-notice).
