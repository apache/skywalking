# UI

The SkyWalking web UI ships from a dedicated project,
[apache/skywalking-horizon-ui](https://github.com/apache/skywalking-horizon-ui).
This OAP distribution does not bundle a UI.

Horizon UI **releases independently** of the OAP backend, on its own
schedule. This repository is **not bound to any specific Horizon UI
version** — backend compatibility is Horizon UI's responsibility, and it
supports OAP from `11.0` onward. Don't lock the UI to an OAP `x.y.z` tag;
pick the UI version following
[Horizon UI's OAP-compatibility notes](https://skywalking.apache.org/docs/skywalking-horizon-ui/next/compatibility/oap-version/).

## Run Horizon UI with Docker

Released images are published to Docker Hub at
[`apache/skywalking-ui`](https://hub.docker.com/r/apache/skywalking-ui) —
use the `latest` tag, or a `horizon-<version>` tag for a reproducible
deploy. Per-commit development images are pushed to
[`ghcr.io/apache/skywalking-horizon-ui`](https://github.com/apache/skywalking-horizon-ui/pkgs/container/skywalking-horizon-ui)
and are for testing the development branch, not production.

Horizon UI is configured through a mounted `horizon.yaml` file (not
environment variables). The OAP wiring it needs is the query host and the
admin host; the Zipkin host is only required if a layer uses Zipkin
traces:

```shell
docker run --name skywalking-ui --restart always -d \
  -p 8081:8081 \
  -v "$PWD/horizon.yaml:/app/horizon.yaml:ro" \
  apache/skywalking-ui:latest
```

A minimal `horizon.yaml` pointed at your OAP cluster:

```yaml
oap:
  queryUrl: http://oap:12800        # GraphQL / query surface
  adminUrl: http://oap:17128        # admin host (runtime-rule, inspect, dsl-debugging, status)
  zipkinUrl: http://oap:9412/zipkin # only if a layer uses Zipkin traces
```

There is no default account — you must configure at least one login
(local users or LDAP) before anyone can sign in. Authentication, RBAC,
sessions, the full `horizon.yaml` reference, and the container layout are
all covered in the Horizon UI docs; don't expect this page to mirror
them.

## What Horizon UI provides

Beyond the application itself, Horizon UI ships the entire visualization
layer that the OAP backend no longer seeds: the default dashboard
configurations for every supported layer, several cross-cutting overview
and 3D infrastructure-map setups, i18n translations, and the sidebar
menu. Operators customize these in the UI's admin pages.

## Documentation

Horizon UI's documentation is the source of truth and tracks the UI
release, not this backend. Start here:

* [Horizon UI documentation](https://skywalking.apache.org/docs/skywalking-horizon-ui/next/readme/)
* [Container image & deployment](https://skywalking.apache.org/docs/skywalking-horizon-ui/next/setup/container-image/)
* [`horizon.yaml` reference](https://skywalking.apache.org/docs/skywalking-horizon-ui/next/setup/horizon-yaml/)
* [OAP connection](https://skywalking.apache.org/docs/skywalking-horizon-ui/next/setup/oap/)
