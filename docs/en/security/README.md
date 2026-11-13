# Security Notice

The SkyWalking OAP server, UI, and agent deployments should run in a secure environment, such as only inside your data center.
OAP server, UI, and agent deployments should only be reachable by the operation team on default
deployment.

All telemetry data are trusted. The OAP server **would not validate any field** of the
telemetry data to avoid extra load for the server. **Every field of every telemetry
category should be validated by the operator before it reaches OAP** — none are
inherently safer than the others.

Examples of surfaces that routinely carry attacker-controllable strings (non-exhaustive):

- **Metrics**: metric names, label keys, label values.
- **Traces**: span operation names, span tags (keys and values), span logs / events,
  endpoint and peer identifiers.
- **Logs**: log body, structured fields.
- **Profiling**: profiling results (eBPF / async-profiler / JFR samples), captured stack
  frames, symbol names.
- **HTTP capture**: HTTP request and response bodies, headers, query
  strings, and dumps collected by agent-side body-capture profiling plugins.

A request URI, a header value, an exception message from poisoned input, or any other
free-form string an instrumented application happens to attach to any of the above will
reach OAP and the UI verbatim. The list grows with every new feature; the operator
contract is "validate everything," not "validate this enumerated set."

It is up to the operator(OPS team) whether to expose the OAP server, UI, or some agent deployment to unsecured
environment.
The following security policies should be considered to add to secure your SkyWalking deployment.

1. HTTPs and gRPC+TLS should be used between agents and OAP servers, as well as UI.
2. Set up TOKEN or username/password based authentications for the OAP server and UI through your Gateway.
3. Validate all fields of the traceable RPC(including HTTP 1/2, MQ) headers(header names are `sw8`, `sw8-x` and `sw8-correlation`) 
   when requests are from out of the trusted zone. Or simply block/remove those headers unless you are using the client-js agent.
4. **All fields of telemetry data should be validated and rejected when malicious** — in
   both HTTP raw-text and encoded Protobuf transports. The scope is every category an
   agent can emit (metrics, traces, logs, profiling results, HTTP capture / debugging
   dumps, and any future telemetry surface), and every field within each category. Treat
   the list above as examples; the rule is "validate every field," not "validate the
   ones we enumerated." None of these surfaces are inherently safer than the others.
5. **Build a validation layer between agents and OAP** as a security enhancement. The
   recommended deployment shape is an operator-controlled gateway / sidecar / service mesh
   that authenticates the source, enforces rate limits, and validates / sanitises every
   telemetry category before forwarding to OAP. Several security vendors offer commercial
   implementations of this layer; the OAP itself does not perform that validation.

Without these protections, an attacker could embed executable Javascript code in any of
those fields, causing XSS or even Remote Code Execution (RCE) issues.

For some sensitive environment, consider to limit the telemetry report frequency in case of DoS/DDoS for exposed OAP
and UI services.

## Admin API Surface (ports 17128 + 17129)

The `admin-server` module hosts a shared HTTP port (default `17128`) for the admin, status,
and debugging REST APIs of the OAP server. Every route on this port must be reachable only by
the operator team. The feature modules mounted on it serve these routes:

| Routes | Module | What a caller can do |
|--------|--------|----------------------|
| `/status/*` | `status` | Read the cluster node list, the alarm rules and alarm runtime state, and the TTL settings. |
| `/debugging/config/dump` | `status` | Read the boot-time configuration of the loaded modules, including the storage, cluster, and Kafka client settings. See [Configuration dump and secrets](#configuration-dump-and-secrets). |
| `/debugging/query/*` | `status` | Run trace, log, topology, and MQE queries with debug tracing, and read their results. |
| `/inspect/*` | `inspect` | Browse the metric catalog, the entities holding values, and the metric values. |
| `/ui-management/*` | `ui-management` | Read, add, update, and disable UI dashboard templates. |
| `/runtime/rule`, `/runtime/rule/*`, `/runtime/mal/*`, `/runtime/lal/*` | `receiver-runtime-rule` | List, dump, add, override, inactivate, and delete MAL/LAL rule files, which are compiled and loaded into the OAP JVM on the fly. |
| `/dsl-debugging/*` | `dsl-debugging` | Start, read, and stop sampling sessions that capture live MAL/LAL/OAL rule executions, including the payloads each sampled rule processed. |
| `/runtime/oal/*` | `dsl-debugging` | List the loaded OAL files and read their rules. |

Of these routes, only `/status/config/ttl` is also served on the public REST port
(`core.restPort`, default `12800`), and it returns the TTL settings only. The admin port also
serves Armeria's `/docs` page, which describes the services registered on that port.

This surface is **far more powerful than the telemetry receiver ports** — a request can
register new Javassist-compiled bytecode, mutate `MeterSystem` state, drop backend schema
(BanyanDB measures), inspect operationally-sensitive payloads (raw log bodies, parsed maps,
metric source events), or read the server's effective configuration.

Before 11.0.0, the status and debugging APIs were served on the core REST port
(`core.restPort`, default `12800`), next to the GraphQL query API. The configuration dump was
added in 9.7.0 by the `debugging-query` module (`SW_DEBUGGING_QUERY`), and the
`/debugging/query/*` routes in 10.1.0. In 10.2.0 the module became `status-query`
(`SW_STATUS_QUERY`) and added the `/status/*` routes. On those versions, block `/status/*` and
`/debugging/*` at the gateway in front of port `12800`, or remove the module by setting its
variable to `-` if it is not needed.

`admin-server` also opens an **admin-internal gRPC bus** (default `17129`) for peer-to-peer
cluster RPCs between OAP nodes — runtime-rule `Suspend / Resume / Forward`, DSL debug
`install / collect / stop`. This is intentionally a **separate transport** from the public
agent / cluster gRPC port (`core.gRPCPort`, default `11800`). An attacker who reaches `11800`
to submit telemetry MUST NOT be able to invoke privileged admin RPCs.

`admin-server` is **enabled by default** so the bundled `status` and `inspect` feature
modules are reachable out of the box. Both ports open with **no built-in authentication**
— the design goal is a simple admin socket that a gateway / service mesh wraps with the
operator's existing auth story.

To remove a feature, set its module variable to `-`, such as `SW_STATUS=-` for the status and
debugging routes (including `/status/config/ttl` on port `12800`). An empty value does not
remove a module: OAP fails to start with a `ProviderNotFoundException`. To close the admin
port entirely, set `SW_ADMIN_SERVER=-` together with `SW_STATUS=-`, `SW_INSPECT=-`,
`SW_UI_MANAGEMENT=-`, `SW_RECEIVER_RUNTIME_RULE=-`, and `SW_DSL_DEBUGGING=-`, as a feature
module left on without `admin-server` stops OAP from starting. Horizon UI reads `/status/*`
and the configuration dump, so keep `status` when Horizon UI is deployed, and restrict the
port instead.

Required operator actions:

1. **Never expose port 17128 (HTTP) to the public internet.** Bind to a private network
   interface or `localhost` and reach it through an operator-controlled gateway.
2. **Never expose port 17129 (admin gRPC) to operators or the agent network.** Bind
   `gRPCHost` to a private peer-to-peer interface reachable ONLY between OAP nodes. This
   port has the same blast radius as port 17128; do not gateway-publish it.
3. **Gateway-protect 17128 with IP allow-list + authentication.** Only the operator team
   should be able to reach the HTTP endpoint.
4. **Audit every request.** Rule content is arbitrary YAML that compiles into the OAP JVM —
   a malicious rule could exfiltrate data, spike resource use, or create metric-name
   collisions. Captured DSL-debug payloads include sensitive operational state. Treat
   `POST /runtime/rule/*` and `POST /dsl-debugging/*` as equivalent to shell access on the
   OAP host.

Without these protections, an attacker with network reach to port 17128 can submit MAL/LAL
rules that are compiled and run inside the OAP JVM, and read the configuration, captured
payloads, and other operational data that the status, debugging, inspect, and DSL debug APIs
return. An attacker who reaches port 17129 can submit rules the same way through the
runtime-rule `Forward` RPC, and install DSL debug sessions on the OAP nodes and collect their
samples. See the
[Admin API](../setup/backend/admin-api/readme.md) doc for the full security notice and
per-feature API surface.

### Configuration dump and secrets

`GET /debugging/config/dump` returns the boot-time settings of the loaded modules, as resolved
from `application.yml` and the environment, plus the BanyanDB settings from `bydb.yml` and
`bydb-topn.yml` when BanyanDB is the storage. A value is replaced by `******` only when its key
contains one of the keywords listed in `status.default.keywords4MaskingSecretsOfConfig`
(case-insensitive):

- A module setting is matched by its own name, such as `password` in
  `storage.elasticsearch.password`.
- A setting inside a map, such as a Kafka client property under the Kafka fetcher's
  `kafkaConsumerConfig`, is matched by its own name inside the map, such as `sasl.jaas.config`,
  not by the name of the map.
- A BanyanDB setting is matched by its full key, such as `storage.banyandb.global.password`.

The value itself is never inspected, so a credential stored under a key that contains no
keyword is returned in clear text. Settings that can carry a credential under such a key include:

- The Kafka fetcher's client properties (`kafka-fetcher.default.kafkaConsumerConfig.*`), such as
  `sasl.jaas.config`, which carries the SASL username and password or the OAuth client secret,
  and `ssl.keystore.key`, which carries a private key.
- The Kafka client settings written as one JSON string: the Zipkin receiver's
  `kafkaConsumerConfig` and the exporter's `kafkaProducerConfig`. A keyword that matches
  `kafkaConsumerConfig` or `kafkaProducerConfig` masks the whole string.
- The ZooKeeper cluster coordinator's ACL `expression` (`SW_ZK_EXPRESSION`), which holds
  `user:password` when `enableACL` is on.
- A JDBC URL (`jdbcUrl`) that includes credentials.

When a configuration carries credentials, add keywords that match its keys. A keyword for a
JSON-string setting or for `jdbcUrl` hides the whole value, including the parts that are not
secret. `SW_DEBUGGING_QUERY_KEYWORDS_FOR_MASKING_SECRETS` replaces the whole list, so keep the
default keywords when you set it, and separate the keywords with commas only, without spaces:

```shell
export SW_DEBUGGING_QUERY_KEYWORDS_FOR_MASKING_SECRETS=user,password,trustStorePass,keyStorePass,token,accessKey,secretKey,authentication,jaas,keystore.key,expression,kafkaConsumerConfig,kafkaProducerConfig,jdbcUrl
```

Masking hides only the values whose keys match a keyword, so review a dump before sharing it.
It is not an access control: anyone who can reach the admin port can read every value the
keywords miss, so restricting the port, as required above, is what protects the dump.

## Client-Side Monitoring

Client-side applications — iOS/iPadOS apps (via OpenTelemetry Swift SDK), browser web apps
(via [client-js](https://github.com/apache/skywalking-client-js)), and WeChat/Alipay
mini-programs (via [mini-program-monitor](https://github.com/SkyAPM/mini-program-monitor)) —
send telemetry data **from the public internet** to OAP endpoints including OTLP/HTTP
(`/v1/traces`, `/v1/logs`, `/v1/metrics`), SkyWalking native (`/v3/segments`), and browser
reporting endpoints.

These endpoints accept data from any client without authentication by default. Apply the
security policies listed above, especially rate limiting, to prevent abuse from untrusted
client-side sources.
