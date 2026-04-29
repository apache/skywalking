# Security Notice

The SkyWalking OAP server, UI, and agent deployments should run in a secure environment, such as only inside your data center.
OAP server, UI, and agent deployments should only be reachable by the operation team on default
deployment.

All telemetry data — including **metrics, traces, and logs** — are trusted. The OAP server
**would not validate any field** of the telemetry data to avoid extra load for the server.
Log data deserves explicit attention here: unlike numeric metric or response-time samples,
log lines are free-form text emitted by applications and routinely contain
attacker-controllable fragments (request URIs, query strings, headers, stack traces from
poisoned input). Treat log payloads as untrusted at the same level as raw HTTP request
bodies.

It is up to the operator(OPS team) whether to expose the OAP server, UI, or some agent deployment to unsecured
environment.
The following security policies should be considered to add to secure your SkyWalking deployment.

1. HTTPs and gRPC+TLS should be used between agents and OAP servers, as well as UI.
2. Set up TOKEN or username/password based authentications for the OAP server and UI through your Gateway.
3. Validate all fields of the traceable RPC(including HTTP 1/2, MQ) headers(header names are `sw8`, `sw8-x` and `sw8-correlation`) 
   when requests are from out of the trusted zone. Or simply block/remove those headers unless you are using the client-js agent.
4. All fields of telemetry data — metrics, traces, **and logs** (HTTP in raw text or encoded
   Protobuf format) — should be validated and reject malicious data. Log fields in particular
   carry attacker-controllable text (request URIs, headers, exception messages from poisoned
   input) and are the most common vector for XSS/RCE payloads landing in the OAP and UI.
5. **Build a validation layer between agents and OAP.** The recommended deployment shape is an
   operator-controlled gateway / sidecar / service mesh between agents and OAP that
   authenticates the source, enforces rate limits, and validates / sanitises telemetry —
   metrics, traces, and logs alike — before forwarding to OAP. Several security vendors offer
   commercial implementations of this layer; the OAP itself does not perform that validation.

Without these protections, an attacker could embed executable Javascript code in those fields,
causing XSS or even Remote Code Execution (RCE) issues. Log data is especially exposed
because applications routinely emit raw user input verbatim into log messages.

For some sensitive environment, consider to limit the telemetry report frequency in case of DoS/DDoS for exposed OAP
and UI services.

## Runtime Rule Admin Surface (port 17128)

The `skywalking-runtime-rule-receiver-plugin` exposes an HTTP admin API on port 17128 that
lets operators **add, override, inactivate, and delete MAL/LAL rule files at runtime** without
restarting OAP. Rules are compiled and loaded into the OAP JVM on the fly. This surface is
**far more powerful than the telemetry receiver ports** — a request can register new Javassist-
compiled bytecode, mutate `MeterSystem` state, and drop backend schema (BanyanDB measures).

The module is **disabled by default**. Enabling it (via `SW_RECEIVER_RUNTIME_RULE=default` or
the YAML selector) opens port 17128 with **no authentication**. This is intentional for now —
the design goal is a simple admin socket that a gateway / service mesh wraps with the
operator's existing auth story.

Required operator actions when enabling:

1. **Never expose port 17128 to the public internet.** Bind to a private network interface or
   `localhost` and reach it through an operator-controlled gateway.
2. **Gateway-protect with IP allow-list + authentication.** Only the operator team should be
   able to reach the endpoint.
3. **Audit every request.** Rule content is arbitrary YAML that compiles into the OAP JVM —
   a malicious rule could exfiltrate data, spike resource use, or create metric-name
   collisions. Treat `POST /runtime/rule/*` as equivalent to shell access on the OAP host.
4. **Keep the port off the cluster-external interface even in cluster mode.** The cluster-
   internal Suspend RPC is registered on the OAP cluster-bus gRPC server (shared with
   RemoteService / HealthCheck) — that is a separate transport from 17128 and follows the
   same security posture as the rest of the cluster bus.

Without these protections an attacker with network reach to port 17128 can execute arbitrary
code inside the OAP JVM. See `docs/en/setup/backend/backend-runtime-rule-api.md` for the full
API surface.

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
