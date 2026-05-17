# UI

The SkyWalking web UI ships from a dedicated project,
[apache/skywalking-horizon-ui](https://github.com/apache/skywalking-horizon-ui).
This OAP distribution does not bundle a UI.

Horizon UI **releases independently** of the OAP backend. The two projects
advance on separate schedules, are matched at deploy time (not at release
time), and the OAP `x.y.z` tag has no implied Horizon UI counterpart. Pin
the UI image tag explicitly in your deployment.

## Run Horizon UI with Docker

Container images are published to
[`ghcr.io/apache/skywalking-horizon-ui`](https://github.com/apache/skywalking-horizon-ui/pkgs/container/skywalking-horizon-ui).
Operators pin to a commit SHA for reproducibility, or use `latest` for tracking.

```shell
docker run --name skywalking-ui --restart always -d \
  -p 8080:8080 \
  -e SW_OAP_ADDRESS=http://oap:12800 \
  -e SW_ADMIN_ADDRESS=http://oap:17128 \
  -e SW_ZIPKIN_ADDRESS=http://oap:9412 \
  ghcr.io/apache/skywalking-horizon-ui:latest
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `SW_OAP_ADDRESS` | `http://127.0.0.1:12800` | OAP public GraphQL / REST surface — read-side queries (services, metrics, traces, logs, alarms, MQE). |
| `SW_ADMIN_ADDRESS` | `http://127.0.0.1:17128` | OAP admin host — `/ui-management/templates/*` (dashboard templates; menu is owned by Horizon UI client-side), `/status/*`, `/inspect/*`, `/dsl-debugging/*`, `/runtime/rule/*`. |
| `SW_ZIPKIN_ADDRESS` | `http://127.0.0.1:9412` | OAP Zipkin v2 query endpoint, for trace ingestion paths that target the Zipkin compatibility layer. |

## Building UI assets

The Horizon UI repository owns its own build, release, and CI workflow. To
contribute dashboards, menus, widgets, or i18n, see the
[Horizon UI contributing guide](https://github.com/apache/skywalking-horizon-ui#contributing).
