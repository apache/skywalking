# Kubernetes (K8s) monitoring from Rover

SkyWalking uses the SkyWalking Rover system to collect access logs from Kubernetes clusters and hands them over to the [OAL system](./../../concepts-and-designs/oal.md) for metrics and entity analysis.

## Data flow
1. SkyWalking Rover monitoring access log data from K8s and send to the OAP.
2. The SkyWalking OAP Server receive access log from Rover through gRPC, analysis the generate entity, and using [OAL](../../concepts-and-designs/oal.md) to generating metrics.

## Setup
1. Setup [Rover in the Kubernetes](https://skywalking.apache.org/docs/skywalking-rover/next/en/setup/deployment/kubernetes/readme/) and enable [access log service](https://skywalking.apache.org/docs/skywalking-rover/next/en/setup/configuration/traffic/).
2. Setup eBPF receiver module by the following configuration.
```yaml
receiver-ebpf:
  selector: ${SW_RECEIVER_EBPF:default}
  default:
```

## Generated Entities

SkyWalking receive the access logs from Rover, analyzes the kubernetes connection information to parse out the following corresponding entities:
1. Service
2. Service Instance
3. Service Endpoint
4. Service Relation
5. Service Instance Relation
6. Service Endpoint Relation

## Generate Metrics

For each of the above-mentioned entities, metrics such as connection, transmission, and protocol can be analyzed.

### Connection Metrics

Record the relevant metrics for every service establishing/closing connections with other services.

| Name                | Unit        | Description                                                  |
|---------------------|-------------|--------------------------------------------------------------|
| Connect CPM         | Count       | Total Connect to other Service counts per minutes.           |
| Connect Duration    | Nanoseconds | Total Connect to other Service use duration.                 | 
| Connect Success CPM | Count       | Success to connect to other Service counts per minutes.      |
| Accept CPM          | Count       | Accept new connection from other Service counts per minutes. | 
| Accept Duration     | Nanoseconds | Total accept new connection from other Service use duration. |
| Close CPM           | Count       | Close one connection counts per minutes.                     |
| Close Duration      | Nanoseconds | Total Close connections use duration.                        |

### Transfer Metrics

Record the basic information and L2-L4 layer details for each syscall made during network requests by every service to other services.

#### Read Data from Connection

| Name                             | Unit        | Description                                                    |
|----------------------------------|-------------|----------------------------------------------------------------|
| Read CPM                         | Count       | Read from connection counts per minutes.                       |
| Read Duration                    | Nanoseconds | Total read data use duration.                                  |
| Read Package CPM                 | Count       | Total read TCP Package count per minutes.                      |
| Read Package Size                | Bytes       | Total read TCP package size per minutes.                       |
| Read Layer 4 Duration            | Nanoseconds | Total read data on the Layer 4 use duration.                   |
| Read Layer 3 Duration            | Nanoseconds | Total read data on the Layer 3 use duration.                   |
| Read Layer 3 Recv Duration       | Nanoseconds | Total read data on the Layer 3 receive use duration.           |
| Read Layer 3 Local Duration      | Nanoseconds | Total read data on the Layer 3 local use duration.             |
| Read Package To Queue Duration   | Nanoseconds | Total duration between TCP package received and send to Queue. |
| Read Package From Queue Duration | Nanoseconds | Total duration between send to Queue and receive from Queue.   |
| Read Net Filter CPM              | Count       | Total Net Filtered count when read data.                       |
| Read Net Filter Duration         | Nanoseconds | Total Net Filtered use duration.                               |

#### Write Data to Connection

| Name                             | Unit        | Description                                                                      |
|----------------------------------|-------------|----------------------------------------------------------------------------------|
| Write CPM                        | Count       | Write to connection counts per minutes.                                          |
| Write Duration                   | Nanoseconds | Total write data to connection use duration.                                     |
| Write Package CPM                | Count       | Total write TCP Package count per minutes.                                       |
| Write Package Size               | Bytes       | Total write TCP Package size per minutes.                                        |
| Write L4 Duration                | Nanoseconds | Total write data to connection Layer 4 use duration.                             |
| Write L3 Duration                | Nanoseconds | Total write data to connection Layer 3 use duration.                             |
| Write L3 Local Duration          | Nanoseconds | Total write data to the connection Layer 3 Local use duration.                   |
| Write L3 Output Duration         | Nanoseconds | Total write data to the connection Layer 3 Output use duration.                  |
| Write L2 Duration                | Nanoseconds | Total write data to connection Layer 2 use duration.                             |
| Write L2 Ready Send Duration     | Nanoseconds | Total write data to the connection Layer 2 ready send data queue use duration.   |
| Write L2 Send NetDevice Duration | Nanoseconds | Total write data to the connection Layer 2 send data to net device use duration. |

### Protocol

Based on each transfer data analysis, extract the information of the 7-layer network protocol.

#### HTTP/1.x or HTTP/2.x

| Name                 | Init        | Description                                      |
|----------------------|-------------|--------------------------------------------------|
| Call CPM             | Count       | HTTP Request calls per minutes.                  |
| Duration             | Nanoseconds | Total HTTP Response use duration.                |
| Success CPM          | Count       | Total HTTP Response success(status < 500) count. |
| Request Header Size  | Bytes       | Total Request Header size.                       |
| Request Body Size    | Bytes       | Total Request Body size.                         |
| Response Header Size | Bytes       | Total Response Header size.                      |
| Response Body Size   | Bytes       | Total Response Body size.                        |

## Customizations
You can customize your own metrics/dashboard panel.
The metrics definition and expression rules are found in `/config/oal/ebpf.oal`, please refer the [Scope Declaration Documentation](../../concepts-and-designs/scope-definitions.md#scopes-with-k8s-prefix).
The K8s dashboard panel configurations are found in `/config/ui-initialized-templates/k8s_service`.
