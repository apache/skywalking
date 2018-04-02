# Token Authentication
## Supported version
5.0.0-beta +

## How need token authentication after we have TLS?
TLS is about transport security, which makes sure the network can be trusted. 
The token authentication is about monitoring application data **can be trusted**.

## Token 
In current version, Token is considered as a simple string.

### Set Token
1. Set token in agent.config file
```properties
# Authentication active is based on backend setting, see application.yml for more details.
agent.authentication = xxxx
```

2. Set token in application.yml file
```yaml
agent_gRPC:
  gRPC:
    host: localhost
    port: 11800

    #Set your own token to active auth
    authentication: xxxxxx
```

## Authentication fails
The Collector verifies every request from agent, allowed only the token match.

If the token is not right, you will see the following log in agent
```
org.apache.skywalking.apm.dependencies.io.grpc.StatusRuntimeException: PERMISSION_DENIED
```

## FAQ
### Can I use token authentication instead of TLS?
No, you shouldn't. In tech way, you can of course, but token and TLS are used for untrusted network env. In that circumstance,
TLS has higher priority than this. Token can be trusted only under TLS protection.Token can be stolen easily if you 
send it through a non-TLS network.

### Do you support other authentication mechanisms? Such as ak/sk?
For now, no. But we appreciate someone contributes this feature. 

