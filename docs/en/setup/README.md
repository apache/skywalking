# Setup
Setup based on which kind of probes are you going to use. If you don't understand, please read [Concepts and Designs](../concepts-and-designs/README.md) first.


**Important: Please comfirm the system time on the monitored servers are same as the time on the OAP servers.**


## Download official releases
- Backend, UI and Java agent are Apache official release, you could find them at [Apache SkyWalking DOWNLOAD page](http://skywalking.apache.org/downloads/).
- Download .Net agent by following [Getting started document](https://github.com/OpenSkywalking/skywalking-netcore#getting-started).
- Download Nodejs agent by following [English document](https://github.com/OpenSkywalking/skywalking-nodejs/blob/master/docs/README.md#documents). 

## Language agents in Service 

- [Java agent](service-agent/java-agent/README.md). Introduce how to install java agent to your service, without change any codes.
- [.NET Core agent](https://github.com/OpenSkywalking/skywalking-netcore). See .NET Core agent project document for more details.
- [Node.js agent](https://github.com/OpenSkywalking/skywalking-nodejs). See Node.js server side agent project document for more details.

## On Service Mesh
  - Istio
    - [SkyWalking on Istio](istio/README.md). Introduce how to use Istio Mixer bypass Adapter to work with SkyWalking.
    

## Setup backend
Follow [backend and UI setup document](backend/backend-ui-setup.md) to understand and config the backend for different
scenarios, and open advanced features.

## Changes log
Backend, UI and Java agent changes are available [here](../../../CHANGES.md).
