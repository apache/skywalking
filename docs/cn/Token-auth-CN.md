# 基于 Token 认证
## 版本支持
5.0.0-beta +

## 在使用了TLS 认证之后,为何还需要基于 Token 的认证?
TLS 是保证传输层的安全,保证传输的网络是可信的.
基于 token 的认证是为了保证应用的监控数据是 **可信的**.

## Token 
在现在的版本中, Token是一个简单的字符串.

### 设置 Token
1. 在 agent.config 文件中设置 Token
```properties
# Authentication active is based on backend setting, see application.yml for more details.
agent.authentication = xxxx
```

2. 在 `application.yml` 文件中设置 token
```yaml
agent_gRPC:
  gRPC:
    host: localhost
    port: 11800

    #Set your own token to active auth
    authentication: xxxxxx
```

## 认证失败
collector验证来自探针的每个请求,只有 token 正确,验证才能通过。

如果token不正确，您将在探针端的日志看到如下日志:
```
org.apache.skywalking.apm.dependencies.io.grpc.StatusRuntimeException: PERMISSION_DENIED
```

## FAQ
### 我可以只使用token认证而不用TLS?
不行. 从技术层面来说, 当然可以.但是token 和 TLS 用于不被信任的网络环境. 在这种情况下, TLS显得更加重要, token 认证仅仅在 TLS 认证的之后才能被信任,
如果在一个没有 TLS 的网络环节中, token非常容易被拦截和窃取.

### 现在skywalking是否支持其他的认证机制? 比如 ak/sk?
现在还不支持,但是如果有人愿意提供这些这些新特性,我们表示感谢.

