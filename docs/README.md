# Welcome
**Here are SkyWalking 7 official documents. You're welcome to join us.**

From here you can learn all about **SkyWalking**’s architecture, how to deploy and use SkyWalking, and develop based on SkyWalking contributions guidelines.

- [Concepts and Designs](en/concepts-and-designs/README.md). You'll find the the most important core ideas about SkyWalking. You can learn from here if you want to understand what is going on under our cool features and visualization.

- [Setup](en/setup/README.md). Guides for installing SkyWalking in different scenarios. As a platform, it provides several ways to provide observability, including monitoring and alarm of course. 

- [Contributing Guides](en/guides/README.md). Guides are for PMC member, committer or new contributor. Here, you can find how to start contributing.

- [Protocols](en/protocols/README.md). Protocols show the communication ways between agents/probes and backend. Anyone interested in uplink telemetry data should definitely read this.

- [FAQs](en/FAQ/README.md). A manifest of already known setup problems, secondary developments experiments. When you are facing a problem, check here first.


In addition, you might find these links interesting:

- The latest and old releases are all available at [Apache SkyWalking release page](http://skywalking.apache.org/downloads/). The change logs are [here](../CHANGES.md).

- [SkyWalking WIKI](https://cwiki.apache.org/confluence/display/SKYWALKING/Home) hosts the context of some changes and events.

- Up-to-date overview of SkyWalking module [call flow](https://sourcespy.com/github/skywalking/xx-omcalls-.html) and [hierarchy](https://sourcespy.com/github/skywalking/xx-omhierarchy-.html) including ability to analize each module individually.

- You can find the speaking schedules at Conf, online videos and articles about SkyWalking in [Community resource catalog](https://github.com/OpenSkywalking/Community).

We're always looking for help improving our documentation and codes, so please don’t hesitate to [file an issue](https://github.com/apache/skywalking/issues/new) 
if you see any problem. 
Or better yet, submit your own contributions through pull request to help make them better.

___
# Document Catalog
If you are already familiar with SkyWalking, you could use this catalog to find the document chapter directly.

* [Concepts and Designs](en/concepts-and-designs/README.md)
  * What is SkyWalking?
    * [Overview and Core concepts](en/concepts-and-designs/overview.md). Provides a high-level description and introduction, including the problems the project solves.
    * [Project Goals](en/concepts-and-designs/project-goals.md). Provides the goals which SkyWalking is trying to focus on and provide features about.
  * Probe
    * [Introduction](en/concepts-and-designs/probe-introduction.md). Lead readers to understand what the probe is, how many different probes exists and why we need them.
    * [Service auto instrument agent](en/concepts-and-designs/service-agent.md). Introduces what the auto instrument agents do and which languages does SkyWalking already support. 
    * [Manual instrument SDK](en/concepts-and-designs/manual-sdk.md). Introduces the role of the manual instrument SDKs in SkyWalking ecosystem.
    * [Service Mesh probe](en/concepts-and-designs/service-mesh-probe.md). Introduces why and how SkyWalking receive telemetry data from Service mesh and proxy probe.
  * Backend
    * [Overview](en/concepts-and-designs/backend-overview.md). Provides a high level introduction about the OAP backend.
    * [Observability Analysis Language](en/concepts-and-designs/oal.md). Introduces the core languages, which are designed for aggregation behaviour definition.
    * [Query in OAP](en/protocols/README.md#query-protocol). A set of query protocol provided, based on the Observability Analysis Language metrics definition.
  * UI
    * [Overview](en/concepts-and-designs/ui-overview.md).  A simple brief about SkyWalking UI.
  * CLI (Command Line Interface)
    * SkyWalking CLI provides a command line interface to interact with SkyWalking backend (via GraphQL), for more information, [click here](https://github.com/apache/skywalking-cli).
* [Setup](en/setup/README.md).
  * Backend, UI, Java agent, and CLI are Apache official release, you could find them at [Apache SkyWalking DOWNLOAD page](http://skywalking.apache.org/downloads/).
  * Language agents in Service
    * [Java agent](en/setup/service-agent/java-agent/README.md). Introduces how to install the java agent to your service, without changing any code.
      * [Supported middleware, framework and library](en/setup/service-agent/java-agent/Supported-list.md).
      * [Agent Configuration Properties](en/setup/service-agent/java-agent/README.md#table-of-agent-configuration-properties).
      * [Optional plugins](en/setup/service-agent/java-agent/README.md#optional-plugins).
      * [Bootstrap/JVM class plugin](en/setup/service-agent/java-agent/README.md#bootstrap-class-plugins).
      * [Advanced features](en/setup/service-agent/java-agent/README.md#advanced-features).
      * [Plugin development guide](en/setup/service-agent/java-agent/README.md#plugin-development-guide).
      * [Agent plugin tests and performance tests](en/setup/service-agent/java-agent/README.md#test).
    * [Other language agents](en/setup/README.md#language-agents-in-service) includes .NetCore, PHP, NodeJS, Go, which are maintained by volunteers.
  * Service Mesh
    * [SkyWalking on Istio](en/setup/istio/README.md). Introduces how to use Istio Mixer bypass Adapter to work with SkyWalking.
    * Use [ALS (access log service)](https://www.envoyproxy.io/docs/envoy/latest/api-v2/service/accesslog/v2/als.proto) to observe service mesh, without Mixer. Follow [document](en/setup/envoy/als_setting.md) to open it.
  * [Backend, UI and CLI setup document](en/setup/backend/backend-ui-setup.md).
    * [Backend setup document](en/setup/backend/backend-setup.md).
      * [Overriding settings](en/setup/backend/backend-setting-override.md) in application.yml is supported.
      * [IP and port setting](en/setup/backend/backend-ip-port.md). Introduces how IP and port set can be used.
      * [Backend init mode startup](en/setup/backend/backend-init-mode.md). How to init the environment and exit graciously. Read this before you try to start a new cluster.
      * [Cluster management](en/setup/backend/backend-cluster.md). Guide about backend server cluster mode.
      * [Deploy in kubernetes](en/setup/backend/backend-k8s.md). Guides you to build and use SkyWalking image, and deploy in k8s.
      * [Choose storage](en/setup/backend/backend-storage.md). As we know, in default quick start, backend is running with H2 DB. But clearly, it doesn't fit the product env. In here, you could find what other choices do you have. Choose the one you like, we also welcome anyone to contribute new storage implementors.
      * [Set receivers](en/setup/backend/backend-receivers.md). You could choose receivers by your requirements, most receivers are harmless, at least our default receivers are. You would set and active all receivers provided.
      * Do [trace sampling](en/setup/backend/trace-sampling.md) at backend. Trace sampling allows you to keep your metrics accurate, whilst only keeping some traces in storage based on rate.
      * Follow [slow DB statement threshold](en/setup/backend/slow-db-statement.md) config document to understand how to detect slow database statements (including SQL statements) in your system.
      * Official [OAL scripts](en/guides/backend-oal-scripts.md). As you known from our [OAL introduction](en/concepts-and-designs/oal.md), most of backend analysis capabilities based on the scripts. Here is the description of official scripts, which helps you to understand which metrics data are in process, also could be used in alarm.
      * [Alarm](en/setup/backend/backend-alarm.md). Alarm provides a time-series based check mechanism. You could set alarm rules targeting the analysis oal metrics objects.
      * [Advanced deployment options](en/setup/backend/advanced-deployment.md). If you want to deploy backend in very large scale and support high loads, you may need this.
      * [Metrics exporter](en/setup/backend/metrics-exporter.md). Use metrics data exporter to forward metrics data to 3rd party systems.
      * [Time To Live (TTL)](en/setup/backend/ttl.md). Metrics and traces are time series data, they would be saved forever, you could set the expired time for each dimension.
      * [Dynamic Configuration](en/setup/backend/dynamic-config.md). Make configuration of OAP changed dynamic, from remote service or 3rd party configuration management system.
      * [Uninstrumented Gateways](en/setup/backend/uninstrumented-gateways.md). Configure gateways/proxies that are not supported by SkyWalking agent plugins, to reflect the delegation in topology graph.
    * [UI setup document](en/setup/backend/ui-setup.md).
    * [CLI setup document](https://github.com/apache/skywalking-cli).
* [Contributing Guides](en/guides/README.md). Guides are for PMC member, committer or new contributor. At here, you can find how to start contributing.
  * [Contact us](en/guides/README.md#contact-us). Guide users about how to contact the official committer team or communicate with the project community.
  * [Process to become official Apache SkyWalking Committer](en/guides/asf/committer.md). How to become an official committer or PMC member.
  * [Compiling Guide](en/guides/How-to-build.md). Follow this to compile the whole project from the source code.
  * [Agent plugin development guide](en/guides/Java-Plugin-Development-Guide.md). Guide developers to write their plugin, and follow [plugin test requirements](en/guides/Plugin-test.md) when you push the plugin to the upstream.
* [Protocols](en/protocols/README.md). Protocols show the communication ways between agents/probes and backend. Anyone interested in uplink telemetry data should definitely read this.
* [FAQs](en/FAQ/README.md). A manifest of already known setup problems, secondary developments experiments. When you are facing a problem, check here first.


