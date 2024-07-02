# Kubernetes (K8s) monitoring from Rover

SkyWalking uses the Cilium Fetcher to gather traffic data between services from Cilium Hubble via the Observe API. It then leverages the [OAL System](./../../concepts-and-designs/oal.md) for metrics and entity analysis.

## Data flow

SkyWalking fetches Cilium Node and Observability Data from gRPC API, analysis to generate entity and using [OAL](./../../concepts-and-designs/oal.md) to generating metrics.

## API Requirements

1. [Peers API](https://github.com/cilium/cilium/blob/main/api/v1/peer/peer_grpc.pb.go#L33-L39): Listen the hubble node in the cluster, OAP would communicate with Hubble node to obtain Observe data.
2. [Observe API](https://github.com/cilium/cilium/blob/main/api/v1/observer/observer_grpc.pb.go#L41): Fetch the Flow data from Hubble node.

## Setup
1. Please following the [Setup Hubble Observability documentation](https://docs.cilium.io/en/stable/gettingstarted/hubble_setup/) to setting the Hubble for provided API.
2. Activate Cilium receiver module to set `selector=default` in the YAML or `set SW_CILIUM_FETCHER=default` through the system environment viriable.
```yaml
cilium-fetcher:
    selector: ${SW_CILIUM_FETCHER:default}
    default:
        peerHost: ${SW_CILIUM_FETCHER_PEER_HOST:hubble-peer.kube-system.svc.cluster.local}
        peerPort: ${SW_CILIUM_FETCHER_PEER_PORT:80}
        fetchFailureRetrySecond: ${SW_CILIUM_FETCHER_FETCH_FAILURE_RETRY_SECOND:10}
        sslConnection: ${SW_CILIUM_FETCHER_SSL_CONNECTION:false}
        sslPrivateKeyFile: ${SW_CILIUM_FETCHER_PRIVATE_KEY_FILE_PATH:}
        sslCertChainFile: ${SW_CILIUM_FETCHER_CERT_CHAIN_FILE_PATH:}
        sslCaFile: ${SW_CILIUM_FETCHER_CA_FILE_PATH:}
        convertClientAsServerTraffic: ${SW_CILIUM_FETCHER_CONVERT_CLIENT_AS_SERVER_TRAFFIC:true}
```
3. If enabled the [TLS certificate within the Hubble](https://docs.cilium.io/en/stable/gettingstarted/hubble-configuration/#tls-certificates), please update these few configurations.
   1. `peerPort`: usually should be updated to the `443`.
   2. `sslConnection`: should be set to `true`.
   3. `sslPrivateKeyFile`: the path of the private key file.
   4. `sslCertChainFile`: the path of the certificate chain file.
   5. `sslCaFile`: the path of the CA file.

## Generated Entities

SkyWalking fetch the flow from Cilium, analyzes the source and destination endpoint to parse out the following corresponding entities:
1. Service
2. Service Instance
3. Service Endpoint
4. Service Relation
5. Service Instance Relation
6. Service Endpoint Relation

## Generate Metrics

For each of the above-mentioned entities, metrics such as L4 and L7 protocols can be analyzed.

### L4 Metrics

Record the relevant metrics for every service read/write packages with other services.

| Name                      | Unit          | Description                                                               |
|---------------------------|---------------|---------------------------------------------------------------------------|
| Read Package CPM          | Count         | Total Read Package from other Service counts per minutes.                 |
| Write Package CPM         | Count         | Total Write Package from other Service counts per minutes.                | 
| Drop Package CPM          | Count         | Total Drop Package from other Service counts per minutes.                 |
| Drop Package Reason Count | Labeled Count | Total Read Package reason(labeled) from other Service counts per minutes. | 

### Protocol

Based on each transfer data analysis, extract the information of the 7-layer network protocol.

NOTE: By default, Cilium only reports L4 metrics. If you need L7 metrics, 
they must be explicitly specified in each service's CiliumNetworkPolicy. For details please [refer to this document](https://docs.cilium.io/en/latest/security/).

#### HTTP

| Name               | Unit        | Description                                             |
|--------------------|-------------|---------------------------------------------------------|
| CPM                | Count       | HTTP Request calls per minutes.                         |
| Duration           | Nanoseconds | Total HTTP Response use duration.                       |
| Success CPM        | Count       | Total HTTP Response success(status < 500) count.        |
| Status 1/2/3/4/5xx | Count       | HTTP Response status code group by 1xx/2xx/3xx/4xx/5xx. |

#### DNS

| Name        | Unit        | Description                                            |
|-------------|-------------|--------------------------------------------------------|
| CPM         | Count       | DNS Request calls per minutes.                         |
| Duration    | Nanoseconds | Total DNS Response use duration.                       |
| Success CPM | Count       | Total DNS Response success(code == 0) count.           |
| Error Count | Label Count | DNS Response error count with error description label. |

#### Kafka

| Name        | Unit        | Description                                              |
|-------------|-------------|----------------------------------------------------------|
| CPM         | Count       | Kafka Request calls per minutes.                         |
| Duration    | Nanoseconds | Total Kafka Response use duration.                       |
| Success CPM | Count       | Total Kafka Response success(errorCode == 0) count.      |
| Error Count | Label Count | Kafka Response error count with error description label. |

## Load Balance for Cilium Fetcher with OAP cluster

The Cilium Fetcher module relies on the Cluster module, when the Cilium Fetcher module starts up, 
it obtains information about all Cilium nodes and node information in the OAP cluster through Peers API on each OAP node. 

Additionally, it averagely distributes collected Cilium nodes to every OAP node. 
Moreover, it ensures that a single Cilium node is not monitored by multiple OAP nodes.

## Customizations
You can customize your own metrics/dashboard panel.
The metrics definition and expression rules are found in `/config/oal/cilium.oal`, please refer the [Scope Declaration Documentation](../../concepts-and-designs/scope-definitions.md#scopes-with-cilium-prefix).
The Cilium dashboard panel configurations are found in `/config/ui-initialized-templates/cilium_service`.
