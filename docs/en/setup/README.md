# Setup
Setup based on which kind of probes are you going to use. If you don't understand, please read [Concepts and Designs](../concepts-and-designs/README.md) first.


**Important: Please choose the timezone on UI, make it matches the timezone of your OAP backend servers.**


## Download official releases
- Backend, UI and Java agent are Apache official release, you could find them at [Apache SkyWalking DOWNLOAD page](http://skywalking.apache.org/downloads/).

## Language agents in Service

- [Java agent](service-agent/java-agent/README.md). Introduce how to install java agent to your service, without change any codes.

The following agents and SDKs are compatible with the SkyWalking's formats and protocols, but maintained by the 3rd-party.
You could go to their project repositories to find out the releases and how to use them.
- [SkyAPM .NET Core agent](https://github.com/SkyAPM/SkyAPM-dotnet). See .NET Core agent project document for more details.
- [SkyAPM Node.js agent](https://github.com/SkyAPM/SkyAPM-nodejs). See Node.js server side agent project document for more details.
- [SkyAPM PHP SDK](https://github.com/SkyAPM/SkyAPM-php-sdk). See PHP agent project document for more details.
- [Tetrate GO2Sky](https://github.com/tetratelabs/go2sky). See GO2Sky project document for more details.

## Service Mesh
  - Istio
    - [SkyWalking on Istio](istio/README.md). Introduce how to use Istio Mixer bypass Adapter to work with SkyWalking.
  - Envoy
    - Use [ALS(access log service)](https://www.envoyproxy.io/docs/envoy/latest/api-v2/service/accesslog/v2/als.proto) to observe service mesh, without Mixer. Follow [document](envoy/als_setting.md) to open it.

## Proxy
  - [Envoy Proxy](https://www.envoyproxy.io/)
    - [Sending metrics to Skywalking from Envoy](envoy/metrics_service_setting.md). How to send metrics from Envoy to SkyWalking using [Metrics service](https://www.envoyproxy.io/docs/envoy/latest/api-v2/config/metrics/v2/metrics_service.proto.html).

## Setup backend
Follow [backend and UI setup document](backend/backend-ui-setup.md) to understand and config the backend for different
scenarios, and open advanced features.

## Changes log
Backend, UI and Java agent changes are available [here](../../../CHANGES.md).
