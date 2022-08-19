# UI
SkyWalking UI distribution is already included in our Apache official release. 

## Startup
Startup script is also in `/bin/webappService.sh`(.bat). UI runs as an OS Java process, powered-by Zuul.

## Settings
The settings file of UI is  `webapp/webapp.yml` in the distribution package. It has three parts.

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

## Start with Docker Image

Start a container to connect OAP server whose address is `http://oap:12800`.

```shell
docker run --name oap --restart always -d -e SW_OAP_ADDRESS=http://oap:12800 apache/skywalking-ui:8.8.0
```

### Configuration

We could set up environment variables to configure this image.

### SW_OAP_ADDRESS

The address of your OAP server. The default value is `http://127.0.0.1:12800`.