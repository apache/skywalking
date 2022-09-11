# Official OAL script
First, read the [OAL introduction](../concepts-and-designs/oal.md) to learn the OAL script grammar and the source concept.

From 8.0.0, you may find the OAL script at `/config/oal/*.oal` of the SkyWalking dist.
You could change it, such as by adding filter conditions or new metrics. Then, reboot the OAP server, and it will come into effect.

All metrics named in this script could be used in alarm and UI query.

# Extension

## Logic Endpoint
In default, SkyWalking only treats the operation name of entry span as the endpoint, which are used in the OAL engine.
Users could declare their custom endpoint names by adding the `logic endpoint` tag manually through agent's plugins or manual APIs.

The logic endpoint is a concept that doesn't represent a real RPC call, but requires the statistic.
The value of `x-le` should be in JSON format. There are two options:
1. Define a new logic endpoint in the entry span as a separate new endpoint. Provide its own endpoint name, latency and status. Suitable for entry and local span.
```json
{
  "name": "GraphQL-service",
  "latency": 100,
  "status": true
}
```
2. Declare the current local span representing a logic endpoint.
```json
{
  "logic-span": true
}
```

### References
1. [Java plugin API](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/java-plugin-development-guide/#extension-logic-endpoint-tag-key-x-le) guides users to write plugins with logic endpoint.
2. Java agent's plugins include native included logic endpoints, also it provides ways to set the tag of logic span. The document could be found [here](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/logic-endpoint/).
