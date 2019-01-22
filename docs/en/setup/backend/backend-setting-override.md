# Setting Override
SkyWalking backend supports setting overrides by system properties and system environment variables. 
You could override the settings in `application.yml`

## System properties key rule
**ModuleName**.**ProviderName**.**SettingKey**.

- Example

  Override `restHost` in this setting segment
  
```yaml
core:
  default:
    restHost: ${SW_CORE_REST_HOST:0.0.0.0}
    restPort: ${SW_CORE_REST_PORT:12800}
    restContextPath: ${SW_CORE_REST_CONTEXT_PATH:/}
    gRPCHost: ${SW_CORE_GRPC_HOST:0.0.0.0}
    gRPCPort: ${SW_CORE_GRPC_PORT:11800}
```

Use command arg
```
-Dcore.default.restHost=172.0.4.12
```

## System environment variables
- Example

  Override `restHost` in this setting segment through environment variables
  
```yaml
core:
  default:
    restHost: ${REST_HOST:0.0.0.0}
    restPort: ${SW_CORE_REST_PORT:12800}
    restContextPath: ${SW_CORE_REST_CONTEXT_PATH:/}
    gRPCHost: ${SW_CORE_GRPC_HOST:0.0.0.0}
    gRPCPort: ${SW_CORE_GRPC_PORT:11800}
```

If the `REST_HOST ` environment variable exists in your operating system and its value is `172.0.4.12`, 
then the value of `restHost` here will be overwritten to `172.0.4.12`, otherwise, it will be set to `0.0.0.0`.

By the way, Placeholder nesting is also supported, like `${REST_HOST:${ANOTHER_REST_HOST:127.0.0.1}}`.
In this case, if the `REST_HOST ` environment variable not exists, but the ```REST_ANOTHER_REST_HOSTHOST``` 
environment variable exists and its value is `172.0.4.12`, then the value of `restHost` here will be overwritten to `172.0.4.12`,
otherwise, it will be set to `127.0.0.1`.




