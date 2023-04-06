# UI
SkyWalking UI distribution is already included in our Apache official release.

## Startup
Startup script is also in `/bin/webappService.sh`(.bat). UI runs as a Java process, powered-by Armeria.

## Settings
The settings file of UI is  `webapp/webapp.yml` in the distribution package. It has three parts.

1. Listening port.
1. Backend connect info.

```yaml
serverPort: ${SW_SERVER_PORT:-8080}

# Comma separated list of OAP addresses, with `http://` or `https://` prefix.
oapServices: ${SW_OAP_ADDRESS:-http://localhost:12800}
zipkinServices: ${SW_ZIPKIN_ADDRESS:http://localhost:9412}
```

## Start with Docker Image

Start a container to connect OAP server whose address is `http://oap:12800`.

```shell
docker run --name oap --restart always -d -e SW_OAP_ADDRESS=http://oap:12800 -e SW_ZIPKIN_ADDRESS=http://oap:9412 apache/skywalking-ui:8.8.0
```

### Configuration

We could set up environment variables to configure this image.

### SW_OAP_ADDRESS

The address of your OAP server. The default value is `http://127.0.0.1:12800`.

### SW_ZIPKIN_ADDRESS

The address of your Zipkin server. The default value is `http://127.0.0.1:9412`.
