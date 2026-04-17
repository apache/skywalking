# Security Notice

The SkyWalking OAP server, UI, and agent deployments should run in a secure environment, such as only inside your data center.
OAP server, UI, and agent deployments should only be reachable by the operation team on default
deployment.

All telemetry data are trusted. The OAP server **would not validate any field** of the telemetry data to avoid extra
load for the server.

It is up to the operator(OPS team) whether to expose the OAP server, UI, or some agent deployment to unsecured
environment.
The following security policies should be considered to add to secure your SkyWalking deployment.

1. HTTPs and gRPC+TLS should be used between agents and OAP servers, as well as UI.
2. Set up TOKEN or username/password based authentications for the OAP server and UI through your Gateway.
3. Validate all fields of the traceable RPC(including HTTP 1/2, MQ) headers(header names are `sw8`, `sw8-x` and `sw8-correlation`) 
   when requests are from out of the trusted zone. Or simply block/remove those headers unless you are using the client-js agent.
4. All fields of telemetry data(HTTP in raw text or encoded Protobuf format) should be validated and reject malicious
   data.

Without these protections, an attacker could embed executable Javascript code in those fields, causing XSS or even
Remote Code Execution (RCE) issues.

For some sensitive environment, consider to limit the telemetry report frequency in case of DoS/DDoS for exposed OAP
and UI services.

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
