# Health Check

Health check intends to provide a unique approach to check the healthy status of OAP server. It includes the health status
of modules, GraphQL and gRPC services readiness.

## Health Checker Module.

Health Checker module could solute how to observe the health status of modules. We can active it by below:
```yaml
health-checker:
  selector: ${SW_HEALTH_CHECKER:default}
  default:
    checkIntervalSeconds: ${SW_HEALTH_CHECKER_INTERVAL_SECONDS:5}
```
Notice, we should enable `telemetry` module at the same time. That means the provider should not be `-` and `none`.

After that, we can query OAP server health status by querying GraphQL:

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

Once some modules are unhealthy, for instance, storage H2 is down. The result might be like below:

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
You could refer to [checkHealth query](https://github.com/apache/skywalking-query-protocol/blob/master/common.graphqls)
for more details.

## The readiness of GraphQL and gRPC

We could opt to above query to check the readiness of GraphQL.

OAP has implemented [gRPC Health Checking Protocol](https://github.com/grpc/grpc/blob/master/doc/health-checking.md).
We could use [grpc-health-probe](https://github.com/grpc-ecosystem/grpc-health-probe) or any other tools to check the
health of OAP gRPC services.

## CLI tool
Please follow the [CLI doc](https://github.com/apache/skywalking-cli#checkhealth) to get the health status score directly through the `checkhealth` command.