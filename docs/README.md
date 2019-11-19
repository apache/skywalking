# Welcome
**Here are SkyWalking 6 official documents. Welcome to join us**

From here you can learn all about **SkyWalking**’s architecture, how to deploy and use SkyWalking, also develop based on SkyWalking.

- [Concepts and Designs](en/concepts-and-designs/README.md). Concepts and designs explain the most important core ideas about
SkyWalking. You can learn from here if you want to understand what is going on under our cool features and visualization.
- [Setup](en/setup/README.md). Setup contains guides for installing SkyWalking in different scenarios. As a platform, it provides
several ways to provide observability, including monitoring and alarm of course. 
- [Contributing Guides](en/guides/README.md). Guides are for PMC member, committer or new contributor. At here, you can know how to contribute from beginning.
- [Protocols](en/protocols/README.md). Protocols show the communication ways between agents/probes and backend. Anyone, interested
in uplink telemetry data, definitely should read this.
- [FAQs](en/FAQ/README.md). Include a manifest, including already known setup problems, secondary developments experiments. When 
you are facing a problem, check here first.


In addition, you might find these links interesting:

- The latest and old releases are all available at [Apache SkyWalking release page](http://skywalking.apache.org/downloads/). The change logs are [here](../CHANGES.md).
- Up-to-date overview of SkyWalking module [call flow](https://sourcespy.com/github/skywalking/xx-omcalls-.html) and [hierarchy](https://sourcespy.com/github/skywalking/xx-omhierarchy-.html) including ability to analize each module individually.
- You can find the speaking schedules at Conf, online videos and articles about SkyWalking in [Community resource catalog](https://github.com/OpenSkywalking/Community).
Also, if you have some related to us, welcome to submit a pull request to add.
- We’re always looking for help improving our documentation and codes, so please don’t hesitate to [file an issue](https://github.com/apache/skywalking/issues/new) 
if you see some problem. 
Or better yet, submit your own contributions through pull request to help make them better.

___
# Document Catalog
If you have been familiar with SkyWalking, you could use this catalog to find the document chapter directly.

* [Concepts and Designs](en/concepts-and-designs/README.md)
  * What is SkyWalking?
    * [Overview and Core concepts](en/concepts-and-designs/overview.md). Provides a high-level description and introduction, including the problems the project solves.
    * [Project Goals](en/concepts-and-designs/project-goals.md). Provides the goals, which SkyWalking is trying to focus and provide features about them.
  * Probe
    * [Introduction](en/concepts-and-designs/probe-introduction.md). Lead readers to understand what the probe is, how many different probes existed and why need them.
    * [Service auto instrument agent](en/concepts-and-designs/service-agent.md). Introduce what the auto instrument agents do and which languages does SkyWalking already support. 
    * [Manual instrument SDK](en/concepts-and-designs/manual-sdk.md). Introduce the role of the manual instrument SDKs in SkyWalking ecosystem.
    * [Service Mesh probe](en/concepts-and-designs/service-mesh-probe.md). Introduce why and how SkyWalking receive telemetry data from Service mesh and proxy probe.
  * Backend
    * [Overview](en/concepts-and-designs/backend-overview.md). Provides a high level introduction about the OAP backend.
    * [Observability Analysis Language](en/concepts-and-designs/oal.md). Introduces the core languages, which is designed for aggregation behaviour definition.
    * [Query in OAP](en/protocols/README.md#query-protocol). A set of query protocol provided, based on the Observability Analysis Language metrics definition.
  * UI
    * [Overview](en/concepts-and-designs/ui-overview.md).  A simple brief about SkyWalking UI.
* [Setup](en/setup/README.md).
  * Backend, UI and Java agent are Apache official release, you could find them at [Apache SkyWalking DOWNLOAD page](http://skywalking.apache.org/downloads/).
  * Language agents in Service
    * [Java agent](en/setup/service-agent/java-agent/README.md). Introduce how to install java agent to your service, without change any codes.
      * [Supported middleware, framework and library](en/setup/service-agent/java-agent/Supported-list.md).
      * [Agent Configuration Properties](en/setup/service-agent/java-agent/README.md#table-of-agent-configuration-properties).
      * [Optional plugins](en/setup/service-agent/java-agent/README.md#optional-plugins).
      * [Bootstrap/JVM class plugin](en/setup/service-agent/java-agent/README.md#bootstrap-class-plugins).
      * [Advanced features](en/setup/service-agent/java-agent/README.md#advanced-features).
      * [Plugin development guide](en/setup/service-agent/java-agent/README.md#plugin-development-guide).
      * [Agent plugin tests and performance tests](en/setup/service-agent/java-agent/README.md#test).
    * [Other language agents](en/setup/README.md#language-agents-in-service) includes .NetCore, PHP, NodeJS, Go, which are maintained by volunteers.
  * Service Mesh
    * [SkyWalking on Istio](en/setup/istio/README.md). Introduce how to use Istio Mixer bypass Adapter to work with SkyWalking.
    * Use [ALS(access log service)](https://www.envoyproxy.io/docs/envoy/latest/api-v2/service/accesslog/v2/als.proto) to observe service mesh, without Mixer. Follow [document](en/setup/envoy/als_setting.md) to open it.
  * [Backend and UI setup document](en/setup/backend/backend-ui-setup.md).
    * [Backend setup document](en/setup/backend/backend-setup.md).
      * [Overriding settings](en/setup/backend/backend-setting-override.md) in application.yml is supported。
      * [IP and port setting](en/setup/backend/backend-ip-port.md). Introduce how IP and port set and be used.
      * [Backend init mode startup](en/setup/backend/backend-init-mode.md). How to init the environment and exit graciously. Read this before you try to initial a new cluster.
      * [Cluster management](en/setup/backend/backend-cluster.md). Guide you to set backend server in cluster mode.
      * [Deploy in kubernetes](en/setup/backend/backend-k8s.md). Guide you to build and use SkyWalking image, and deploy in k8s.
      * [Choose storage](en/setup/backend/backend-storage.md). As we know, in default quick start, backend is running with H2 DB. But clearly, it doesn't fit the product env. In here, you could find what other choices do you have. Choose the one you like, we are also welcome anyone to contribute new storage implementor,
      * [Set receivers](en/setup/backend/backend-receivers.md). You could choose receivers by your requirements, most receivers are harmless, at least our default receivers are. You would set and active all receivers provided.
      * Do [trace sampling](en/setup/backend/trace-sampling.md) at backend. This sample keep the metrics accurate, only don't save some of traces in storage based on rate.
      * Follow [slow DB statement threshold](en/setup/backend/slow-db-statement.md) config document to understand that, how to detect the Slow database statements(including SQL statements) in your system.
      * Official [OAL scripts](en/guides/backend-oal-scripts.md). As you known from our [OAL introduction](en/concepts-and-designs/oal.md), most of backend analysis capabilities based on the scripts. Here is the description of official scripts, which helps you to understand which metrics data are in process, also could be used in alarm.
      * [Alarm](en/setup/backend/backend-alarm.md). Alarm provides a time-series based check mechanism. You could set alarm  rules targeting the analysis oal metrics objects.
      * [Advanced deployment options](en/setup/backend/advanced-deployment.md). If you want to deploy backend in very large scale and support high payload, you may need this. 
      * [Metrics exporter](en/setup/backend/metrics-exporter.md). Use metrics data exporter to forward metrics data to 3rd party system.
      * [Time To Live (TTL)](en/setup/backend/ttl.md). Metrics and trace are time series data, they would be saved forever, you could  set the expired time for each dimension.
      * [Dynamic Configuration](en/setup/backend/dynamic-config.md). Make configuration of OAP changed dynamic, from remote service or 3rd party configuration management system.
      * [Uninstrumented Gateways](en/setup/backend/uninstrumented-gateways.md). Configure gateways/proxies that are not supported by SkyWalking agent plugins, to reflect the delegation in topology graph.
    * [UI setup document](en/setup/backend/ui-setup.md).
* [Contributing Guides](en/guides/README.md). Guides are for PMC member, committer or new contributor. At here, you can know how to contribute from beginning.
  * [Contact us](en/guides/README.md#contact-us). Guide users about how to contact the official committer team or communicate with the project community.
  * [Process to become official Apache SkyWalking Committer](en/guides/asf/committer.md). How to become an official committer or PMC member.
  * [Compiling Guide](en/guides/How-to-build.md). Follow this to compile the whole project from the source code.
  * [Agent plugin development guide](en/guides/Java-Plugin-Development-Guide.md). Guide the developer to write their plugin, and follow [plugin test requirement](en/guides/Plugin-test.md) when you push the plugin to the upstream.
* [Protocols](en/protocols/README.md). Protocols show the communication ways between agents/probes and backend. Anyone, interested in uplink telemetry data, definitely should read this.
* [FAQs](en/FAQ/README.md). Include a manifest, including already known setup problems, secondary developments experiments. When  you are facing a problem, check here first.


___
### Users from 5.x
SkyWalking 5.x is still supported by the community. For the user plans to upgrade from 5.x to 6.x, you should know there are some definitions of concepts changed.

The most important two changed concepts are
1. Application(in 5.x) is changed to **Service**(in 6.x), also Application Instance is changed to **Service Instance**.
1. Service(in 5.x) is changed to **Endpoint**(in 6.x).


