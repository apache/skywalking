# Kubernetes Network monitoring
SkyWalking leverages [SkyWalking Rover](https://github.com/apache/skywalking-rover) [network profiling feature](https://skywalking.apache.org/docs/skywalking-rover/next/en/setup/configuration/profiling/#network) for collecting metrics data from the network. SkyWalking Rover converts data from socket data to metrics using eBPF technology.

## Data flow
1. SkyWalking OAP server observes which specific k8s pod needs to monitor the network.
2. SkyWalking Rover receives tasks from SkyWalking OAP server and executes them, and converts the network data into metrics send to the backend service.
3. The SkyWalking OAP Server accesses K8s's `API Server` to fetch meta info and parses the expression with [MAL](../../concepts-and-designs/mal.md) to aggregate.

## Setup
1. Setup [SkyWalking Rover](https://skywalking.apache.org/docs/skywalking-rover/next/en/setup/overview/).
2. Enable the network profiling MAL file in the OAP server.
```yaml
agent-analyzer:
  selector: ${SW_AGENT_ANALYZER:default}
  default:
    meterAnalyzerActiveFiles: ${SW_METER_ANALYZER_ACTIVE_FILES:network-profiling}
```

## Supported metrics

After SkyWalking OAP server receives the metrics from the SkyWalking Rover, it supports to analysis the following data:
1. **Topology**: Based on the process and peer address, the following topology data is supported:
   1. **Relation**: Analyze the relationship between local processes, or local process with external pods or services.
   2. **SSL**: The socket read or write package with SSL.
   3. **Protocol**: The protocols for write or read data.
2. TCP socket read and write metrics, including following types:
   1. **Call Per Minute**: The count of the socket read or write.
   2. **Bytes**: The package size of the socket data.
   3. **Execute Time**: The executed time of the socket read or write.
   4. **Connect**: The socket connect/accept with peer address count and execute time.
   5. **Close**: The socket close the socket count and execute time.
   6. **RTT**: The RTT(Round Trip Time) of socket communicate with peer address.
3. Local process communicate with peer address exception data, including following types:
   1. **Retransmit**: The count of TCP package is retransmitted.
   2. **Drop**: The count of TCP package is dropped.
