# Setup
The document explains how to install Skywalking based on the kind of probes you are going to use.
If you don't understand, please read [Concepts and Designs](../concepts-and-designs/README.md) first.


**Important: Don't forget to configure the timezone on your UI, and you also need to be sure your OAP backend servers are also using the same timezone.**

If you have any issues, please check that your issue is not already described in the [FAQ](../FAQ/README.md).

## Download official releases
- Backend, UI and Java agent are Apache official release, you can find them on the [Apache SkyWalking download page](http://skywalking.apache.org/downloads/).

## Language agents in Service

- [Java agent](service-agent/java-agent/README.md). Introduces how to install java agent to your service, without any impact in your code.

- [LUA agent](https://github.com/apache/skywalking-nginx-lua). Introduce how to install the lua agent in Nginx + LUA module or OpenResty.

- [Python Agent](https://github.com/apache/skywalking-python). Introduce how to install the Python Agent in a Python service.

- [Node.js agent](https://github.com/apache/skywalking-nodejs). Introduce how to install the NodeJS Agent in a NodeJS service.

The following agents and SDKs are compatible with the SkyWalking's data formats and network protocols, but are maintained by 3rd-parties.
You can go to their project repositories for additional info about guides and releases.

- [SkyAPM .NET Core agent](https://github.com/SkyAPM/SkyAPM-dotnet). See .NET Core agent project document for more details.

- [SkyAPM Node.js agent](https://github.com/SkyAPM/SkyAPM-nodejs). See Node.js server side agent project document for more details.

- [SkyAPM PHP agent](https://github.com/SkyAPM/SkyAPM-php-sdk). See PHP agent project document for more details.

- [SkyAPM Go SDK](https://github.com/SkyAPM/go2sky). See go2sky project document for more details.

- [SkyAPM C++ SDK](https://github.com/SkyAPM/cpp2sky). See cpp2sky project document for more details.

## Browser Monitoring
[Apache SkyWalking Client JS](https://github.com/apache/skywalking-client-js). Support collecting metrics and error logs
for the Browser or JavaScript based mobile app. 

Note, make sure the [`receiver-browser`](backend/backend-receivers.md) has been opened, default is **ON** since 8.2.0.

## Service Mesh

  - Istio
    - [SkyWalking on Istio](istio/README.md). Introduces how to analyze Istio metrics.
  - Envoy
    - Use [ALS (access log service)](https://www.envoyproxy.io/docs/envoy/latest/api-v2/service/accesslog/v2/als.proto) to observe service mesh, without Mixer. Follow [document](envoy/als_setting.md) for guides.

## Proxy

  - [Envoy Proxy](https://www.envoyproxy.io/)
    - [Sending metrics to Skywalking from Envoy](envoy/metrics_service_setting.md). How to send metrics from Envoy to SkyWalking using [Metrics service](https://www.envoyproxy.io/docs/envoy/latest/api-v2/config/metrics/v2/metrics_service.proto.html).

## Setup backend

Follow [backend and UI setup document](backend/backend-ui-setup.md) to understand how the backend and UI configuration works. Different scenarios and advanced features are also explained.

## Changes log

Backend, UI and Java agent changes are available [here](../../../CHANGES.md).
