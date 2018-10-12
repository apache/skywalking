# Setting Override
SkyWalking backend supports setting overrides by system properties. 
You could override the settings in `application.yml`

- System properties key rule
**ModuleName**.**ProviderName**.**SettingKey**.

- Example

  Override `restHost` in this setting segment
```yaml
core:
  default:
    restHost: 0.0.0.0
    restPort: 12800
    restContextPath: /
    gRPCHost: 0.0.0.
    gRPCPort: 11800
```

Use command arg
```
-core.default.restHost=172.0.4.12
```