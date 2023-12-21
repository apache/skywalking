# Motivation
SkyWalking has provided an access log collector based on the Agent layer and Service Mesh layer, 
and can generate corresponding topology maps and metrics based on the data. However, the Kubernetes Layer still lacks 
corresponding access log collector and analysis work.

This proposal is dedicated to collecting and analyzing network access logs in Kubernetes.

# Architecture Graph
There is no significant architecture-level change. Still using the Rover project to collect data and report it to 
SkyWalking OAP using the gRPC protocol.

# Propose Changes

Based on the content in Motivation, if we want to ignore the application types(different program languages) and 
only monitor network logs, using eBPF is a good choice. It mainly reflects in the following aspects:

1. Non-intrusive: When monitoring network access logs with eBPF, the application do not need to make any changes to be monitored.
2. Language-unrestricted: Regardless of which programming language is used in the application, network data will ultimately be accessed through [Linux Syscalls](https://linasm.sourceforge.net/docs/syscalls/network.php). Therefore, we can monitor network data by attaching eBPF to the syscalls layer, thus ignoring programming languages.
3. Kernel interception: Since eBPF can attach to the kernel methods, it can obtain the execution status of each packet at L2-L4 layers and generate more detailed metrics.

Based on these reasons and collected data, they can be implemented in SkyWalking Rover and collected and monitored based on the following steps:

1. Monitor the network execution status of all processes in Kubernetes when the Rover system starts.
2. Periodically report data content via [gRPC protocol](https://github.com/apache/skywalking-data-collect-protocol/blob/master/ebpf/accesslog.proto) to SkyWalking OAP.
3. SkyWalking OAP parses network access logs and generates corresponding network topology, metrics, etc.

## Limitation

For content that uses TLS for data transmission, Rover will detect whether the current language uses libraries 
such as [OpenSSL](https://www.openssl.org/). If it is used, it will asynchronously intercept relevant OpenSSL methods 
when the process starts to perceive the original data content.

However, this approach is not feasible for Java because Java does not use the OpenSSL library but performs encryption/decryption 
through Java code. Currently, eBPF cannot intercept Java method calls. Therefore, it results in an inability to perceive 
the TLS data protocol in Java.

### Service with Istio sidecar scenario

If the Service is deployed in Istio sidecar, it will still monitor each process. 
If the Service is a Java service and uses TLS, it can analyze the relevant traffic generated in the sidecar (envoy).

# Imported Dependencies libs and their licenses.

No new library is planned to be added to the codebase.

# Compatibility

About the **protocol**, there should be no breaking changes, but enhancements only:

1. `Rover`: adding a new gRPC data collection protocol for reporting the [access logs](https://github.com/apache/skywalking-data-collect-protocol/blob/master/ebpf/accesslog.proto).
2. `OAP`: It should have no protocol updates. The existing query protocols are already sufficient for querying Kubernetes topology and metric data.

## Data Generation

### Entity

* service_traffic

| column     | data type | value description       |
|------------|-----------|-------------------------|
| name       | string    | kubernetes service name | 
| short_name | string    | same with name          | 
| service_id | string    | base64(name).1          |
| group      | string    | empty string            | 
| layer      | string    | KUBERNETES              |

* instance_traffic

| column     | data type | value description                              |
|------------|-----------|------------------------------------------------|
| service_id | string    | base64(service_name).1                         |
| name       | string    | pod name                                       | 
| last_ping  | long      | last access log message timestamp(millisecond) |
| properties | json      | empty string                                   |

* endpoint_traffic

| column     | data type | value description                           |
|------------|-----------|---------------------------------------------|
| service_id | string    | base64(service_name).1                      |
| name       | string    | access log endpoint name(for HTTP1, is URI) |

### Entity Relation

All entity information is built on connections. If the target address is remote, the name will be resolved in the following order:

1. If it is a pod IP, it will be resolved as pod information.
2. If it is a service IP, it will be resolved as service information.
3. If neither exists, only pod information will be displayed.

Different entities have different displays for remote addresses. Please refer to the following table.

| table name        | remote info(display by following order) |
|-------------------|-----------------------------------------|
| service_relation  | service name, remote IP address         |
| instance_relation | pod name, remote IP address             |

**NOTICE**: If it is the internal data interaction within the pod, such as exchanging data between services and sidecar (envoy), 
no corresponding traffic will be generated. We only generate and interact with external pods.

#### Limitation

If the service IP is used to send requests to the upstream, we will use eBPF to perceive the real target PodIP by 
perceiving relevant [conntrack records](https://en.wikipedia.org/wiki/Netfilter#Connection_tracking).

However, if conntrack technology is not used, it is difficult to perceive the real target IP address. 
In this case, instance relation data of this kind will be **dropped,** but we will mark all discarded relationship 
generation counts through a metric for better understanding of the situation.

### Metrics

Integrate the data into the OAL system and generate corresponding metrics through predefined data combined with OAL statements.

# General usage docs

This proposal will only add a module to Rover that explains the configuration of access logs, and changes in the Kubernetes module on the UI.

In the Kubernetes UI, users can see the following additions:
1. Topology: A topology diagram showing the calling relationships between services, instances, and processes.
2. Entity Metrics: Metric data for services, instances, and processes.
3. Call Relationship Metrics: Metrics for call relationships between different entities.
