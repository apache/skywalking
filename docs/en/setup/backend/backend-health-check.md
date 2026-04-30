# Health Check

Health check intends to provide a unique approach to checking the health status of the OAP server. It includes the health status
of modules, GraphQL, and gRPC services readiness.

> 0 means healthy, and more than 0 means unhealthy.
> less than 0 means that the OAP doesn't start up.

## Health Checker Module.

The Health Checker module helps observe the health status of modules. You may activate it as follows:
```yaml
health-checker:
  selector: ${SW_HEALTH_CHECKER:default}
  default:
    checkIntervalSeconds: ${SW_HEALTH_CHECKER_INTERVAL_SECONDS:5}
```
Note: The `telemetry` module should be enabled at the same time. This means that the provider should not be `-` and `none`.

After that, we can check the OAP server health status by querying the http endpoint: `/healthcheck`,
see [the health check http endpoint doc](../../api/health-check.md).

You can also query the healthiness via other methods like GraphQL, see following.
```
query{
  checkHealth{
    score
    details
  }
}
```

If the OAP server is healthy, the response should be

```json
{
  "data": {
    "checkHealth": {
      "score": 0,
      "details": ""
    }
  }
}
```

If some modules are unhealthy (e.g. storage H2 is down), then the result may look as follows:

```json
{
  "data": {
    "checkHealth": {
      "score": 1,
      "details": "storage_h2,"
    }
  }
}
```
Refer to [checkHealth query](https://github.com/apache/skywalking-query-protocol/blob/master/common.graphqls)
for more details.

## The readiness of GraphQL and gRPC

Use the query above to check the readiness of GraphQL.

OAP has implemented the [gRPC Health Checking Protocol](https://github.com/grpc/grpc/blob/master/doc/health-checking.md).
You may use the [grpc-health-probe](https://github.com/grpc-ecosystem/grpc-health-probe) or any other tools to check the
health of OAP gRPC services.

## CLI tool

The `swctl` CLI ships a `health` subcommand that runs the GraphQL `checkHealth`
query (and, by default, the gRPC `HealthCheck` service) and exits with a
non-zero status when the OAP is unhealthy.

```bash
# Plain gRPC
swctl --base-url=http://OAP:12800/graphql health

# OAP gRPC with TLS (cert verification is intentionally skipped)
swctl --base-url=http://OAP:12800/graphql health --grpcTLS=true
```

### Reading the response

A healthy OAP returns the same `score: 0` envelope shown in the GraphQL
section above and the process exits 0. A failing run prints the GraphQL /
gRPC error and exits non-zero — straightforward to wire into a shell readiness
loop:

```bash
if swctl --base-url=http://OAP:12800/graphql health >/dev/null 2>&1; then
  echo "OAP healthy"
else
  echo "OAP not healthy"
  exit 1
fi
```
