# Token Authentication
## Supported version
7.0.0+

## Why do we need token authentication after TLS?
TLS is about transport security, ensuring a trusted network. 
On the other hand, token authentication is about monitoring **whether application data can be trusted**.

## Token 
In the current version, a token is considered a simple string.

### Set Token
1. Set token in `agent.config` file
```properties
# Authentication active is based on backend setting, see application.yml for more details.
agent.authentication = ${SW_AGENT_AUTHENTICATION:xxxx}
```

2. Set token in `application.yml` file
```yaml
······
receiver-sharing-server:
  default:
    authentication: ${SW_AUTHENTICATION:""}
······
```

## Authentication failure
The Skywalking OAP verifies every request from the agent and only allows requests whose token matches the one configured in `application.yml` to pass through.

If the token does not match, you will see the following log in the agent:
```
org.apache.skywalking.apm.dependencies.io.grpc.StatusRuntimeException: PERMISSION_DENIED
```

## FAQ
### Can I use token authentication instead of TLS?
No, you shouldn't. Of course, it's technically possible, but token and TLS are used for untrusted network environments. In these circumstances,
TLS has a higher priority. Tokens can be trusted only under TLS protection, and they can be easily stolen if sent through a non-TLS network.

### Do you support other authentication mechanisms, such as ak/sk?
Not for now. But we welcome contributions to this feature. 
