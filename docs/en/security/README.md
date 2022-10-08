# Security Notice

The SkyWalking OAP server and UI should run in a secure environment, such as only inside your data center.
OAP server, UI, and all agents deployment should only be reachable by the operation team only on default
deployment.

All telemetry data are trusted. The OAP server **would not validate any field** of the telemetry data to avoid extra
load for the server.

It is up to the operator(OPS team) whether to expose the OAP server, UI, or some agent deployment to unsecured
environment.
The following security policies should be considered to add to secure your SkyWalking deployment.

1. HTTPs and gRPC+TLS should be used between agents and OAP servers, as well as UI.
2. Set up TOKEN or username/password based authentications for the OAP server and UI through your Gateway.
3. Validate all fields in the body of the traceable RPC(including HTTP 1/2, MQ) headers when requests are from out of
   the trusted zone.
4. All fields of telemetry data(HTTP in raw text or encoded Protobuf format) should be validated and reject malicious
   data.

Without these protections, an attacker could embed executable Javascript code in those fields, causing XSS or even
Remote Code Execution (RCE) issues.

For some sensitive environment, consider to limit the telemetry report frequency in case of DoS/DDoS for exposed OAP
and UI services.

## appendix

The SkyWalking [client-js](https://github.com/apache/skywalking-client-js) agent is always running out of the secured
environment. Please follow its **security notice** for more details.