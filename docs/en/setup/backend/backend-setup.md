# Backend setup
First and most important thing is, SkyWalking backend startup behaviours are driven by `config/application.yml`.
Understood the setting file will help you to read this document.

## Startup script
The default startup scripts are `/bin/oapService.sh`(.bat). 
Read [start up mode](backend-start-up-mode.md) document to know other options
of starting backend.


## application.yml
The core concept behind this setting file is, SkyWalking collector is based on pure modularization design. 
End user can switch or assemble the collector features by their own requirements.

So, in `application.yml`, there are three levels.
1. **Level 1**, module name. Meaning this module is active in running mode.
1. **Level 2**, provider name. Set the provider of the module.
1. **Level 3**. settings of the provider.

Example:
```yaml
core:
  default:
    restHost: 0.0.0.0
    restPort: 12800
    restContextPath: /
    gRPCHost: 0.0.0.0
    gRPCPort: 11800
```
1. **core** is the module.
1. **default** is the default implementor of core module.
1. `restHost`, `restPort`, ... `gRPCHost` are all setting items of the implementor.

At the same time, modules includes required and optional, the required modules provide the skeleton of backend,
even modularization supported pluggable, remove those modules are meaningless. We highly recommend you don't try to
change APIs of those modules, unless you understand SkyWalking project and its codes very well.

List the required modules here
1. **Core**. Do basic and major skeleton of all data analysis and stream dispatch.
1. **Cluster**. Manage multiple backend instances in a cluster, which could provide high throughputs process
capabilities.
1. **Storage**. Make the analysis result persistence.
1. **Query**. Provide query interfaces to UI.

For **Cluster** and **Storage** have provided multiple implementors(providers), see **Cluster management**
and **Choose storage** documents in the [link list](#advanced-feature-document-link-list).

Also, several **receiver** modules are provided.
Receiver is the module in charge of accepting incoming data requests to backend. Most(all) provide 
service by some network(RPC) protocol, such as gRPC, HTTPRestful.  
The receivers have many different module names, you could
read **Set receivers** document in the [link list](#advanced-feature-document-link-list).

## Advanced feature document link list
After understand the setting file structure, you could choose your interesting feature document.
We recommend you to read the feature documents in our following order.

1. [Overriding settings](backend-setting-override.md) in application.yml is supported
1. [IP and port setting](backend-ip-port.md). Introduce how IP and port set and be used.
1. [Backend init mode startup](backend-init-mode.md). How to init the environment and exit graciously.
Read this before you try to initial a new cluster.
1. [Cluster management](backend-cluster.md). Guide you to set backend server in cluster mode.
1. [Deploy in kubernetes](backend-k8s.md). Guide you to build and use SkyWalking image, and deploy in k8s.
1. [Choose storage](backend-storage.md). As we know, in default quick start, backend is running with H2
DB. But clearly, it doesn't fit the product env. In here, you could find what other choices do you have.
Choose the one you like, we are also welcome anyone to contribute new storage implementor,
1. [Set receivers](backend-receivers.md). You could choose receivers by your requirements, most receivers
are harmless, at least our default receivers are. You would set and active all receivers provided.
1. [Token authentication](backend-token-auth.md). You could add token authentication mechanisms to avoid `OAP` receiving untrusted data.  
1. Do [trace sampling](trace-sampling.md) at backend. This sample keep the metrics accurate, only don't save some of traces
in storage based on rate.
1. Follow [slow DB statement threshold](slow-db-statement.md) config document to understand that, 
how to detect the Slow database statements(including SQL statements) in your system.
1. Official [OAL scripts](../../guides/backend-oal-scripts.md). As you known from our [OAL introduction](../../concepts-and-designs/oal.md),
most of backend analysis capabilities based on the scripts. Here is the description of official scripts,
which helps you to understand which metrics data are in process, also could be used in alarm.
1. [Alarm](backend-alarm.md). Alarm provides a time-series based check mechanism. You could set alarm 
rules targeting the analysis oal metrics objects.
1. [Advanced deployment options](advanced-deployment.md). If you want to deploy backend in very large
scale and support high payload, you may need this. 
1. [Metrics exporter](metrics-exporter.md). Use metrics data exporter to forward metrics data to 3rd party
system.
1. [Time To Live (TTL)](ttl.md). Metrics and trace are time series data, they would be saved forever, you could 
set the expired time for each dimension.
1. [Dynamic Configuration](dynamic-config.md). Make configuration of OAP changed dynamic, from remote service
or 3rd party configuration management system.
1. [Uninstrumented Gateways](uninstrumented-gateways.md). Configure gateways/proxies that are not supported by SkyWalking agent plugins,
to reflect the delegation in topology graph.

## Telemetry for backend
OAP backend cluster itself underlying is a distributed streaming process system. For helping the Ops team,
we provide the telemetry for OAP backend itself. Follow [document](backend-telemetry.md) to use it.

## Agent hot reboot trigger mechanism in OAP server upgrade
**IMPORTANT**: Agent hot reboot requires both of the OAP nodes and agents to be version 6.3.0 or higher.
The reboot procedure works by the heartbeat between OAP nodes and the agents:
1. The agent sends a heartbeat package to the OAP server;
1. The OAP server just restarted and found no metadata for this agent, then it sends a reset command to the specific agent;
1. The agent received the reset command and re-register itself to the OAP node.

The agent reboot mechanism is not designed for every scenarios where agent need to reboot, but only the scenario where
the backend servers are to be upgraded with all storage data deleted/erased, therefore, there're some noteworthy limitations:
1. Partially deleting the storage data may not work as expected, you **MUST** delete all the storage data.
1. Set an appropriate threshold of config `agent.cool_down_threshold` to wait before the agents re-registering themselves to backend 
to avoid "dirty data", see [`agent.cool_down_threshold`](../service-agent/java-agent/README.md#table-of-agent-configuration-properties)
for more detail.

## FAQs
#### When and why do we need to set Timezone?
SkyWalking provides downsampling time series metrics features. 
Query and storage at each time dimension(minute, hour, day, month metrics indexes)
related to timezone when doing time format. 

For example, metrics time will be formatted like YYYYMMDDHHmm in minute dimension metrics,
which format process is timezone related.
  
In default, SkyWalking OAP backend choose the OS default timezone.
If you want to override it, please follow Java and OS documents to do so.
