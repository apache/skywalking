# Token Authentication

## Token 
In current version, Token is considered as a simple string.

### Set Token
Set token in agent.config file
```properties
# Authentication active is based on backend setting, see application.yml for more details.
agent.authentication = xxxx
```

Meanwhile, open the [backend token authentication](../../backend/backend-token-auth.md).

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

