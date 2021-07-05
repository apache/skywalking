# UI
SkyWalking UI distribution is already included in our Apache official release. 

## Startup
Startup script is also in `/bin/webappService.sh`(.bat). UI runs as an OS Java process, powered-by Zuul.

## Settings
Setting file of UI is  `webapp/webapp.yml` in distribution package. It is constituted by three parts.

1. Listening port.
1. Backend connect info.

```yaml
server:
  port: 8080
spring:
  cloud:
    gateway:
      routes:
        - id: oap-route
          uri: lb://oap-service
          predicates:
            - Path=/graphql/**
    discovery:
      client:
        simple:
          instances:
            oap-service:
              # Point to all backend's restHost:restPort, split by URI arrays.
              - uri: http://127.0.0.1:12800
              - uri: http://instance-2:12800

```

