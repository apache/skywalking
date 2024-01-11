# Create and detect Service Hierarchy Relationship

## Motivation

Service relationship is one of the most important parts of collaborating data in the APM. Service Map is supported for
years from tracing to trace analysis. But still due to the means of the probs, a service could be detected from multiple
methods, which is the same service in multiple
layers. [v9 proposal](https://github.com/apache/skywalking/discussions/8241) mentioned the concept of the layer.
Through this proposal, we plan to establish a kernel-level concept to connect services detected in different layers.

## Architecture Graph

There is no significant architecture-level change.

## Propose Changes

The data sources of SkyWalking APM have covered traditional agent installed service, VMs, cloud infra, k8s, etc.

For example, a Java service is built in a docker image and is going to be deployed in a k8s cluster, with a sidecar
injected due to service mesh managed. The following services would be able to detect cross-layers

1. Java service, detected as Java agent installed.
2. A pod of k8s service is detected, due to k8s layer monitoring.
3. Side car perspective service is detected.
4. VM Linux monitoring for a general process, as the container of Java service is deployed on this specific k8s node.
5. Virtual databases, caches, and queues conjectured by agents, and also monitored through k8s monitoring, even traffic
   monitored by service mesh.

All these services have logic connections or are identical from a physical perspective, but currently, they may be just
similar on name(s), no further metadata connection.

By those, we have a chance to move one step ahead to connect the dots of the whole infrastructure. This means, for the
first time, we are going to establish the connections among services detected from various layers.

**In the v10, I am proposing a new concept `Service Hierarchy`.** Service Hierarchy defines the relationships of
existing services in various layers. With more kinds of agent tech involved(such as eBPF) and deployment tools(such as
operator and agent injector), we could inject relative service/instance metadata and try to build the connections,
including,

- Agent injector injects the pod ID into the system env, then Java agent could report the relationship through system
  properties.
- Rover(eBPF agent) reveals its next iteration forward k8s monitoring rather than profiling. And add the capabilities to
  establish connections among k8s pods and service mesh srv.

Meanwhile, as usual with the new major version change, I would expect UI side changes as well. UI should have flexible
capabilities to show hierarchy services from the service view and topology view. Also, we could consider a deeper view
of the instance part as well.

## Imported Dependencies libs and their licenses.

No new library is planned to be added to the codebase.

## Compatibility

About the **protocol**, there should be no breaking changes, but enhancements only. New query protocols(
service-hierarchy and instance-hierarchy) are considered to be added, some new fields should be added on things like
topology query and instance dependencies to list relative services/instances from other layers directly rather than an
extra query.

About the data structure, due to the new data concept is going to be created, service hierarchy relative data models are
going to be added. If the user is using Elasticsearch and BanyanDB, this should be compatible, they just need to
re-run `init-mode` OAP to extend the existing models. But for SQL database users(MySQL, PostgreSQL), this could require
new tables.

### GraphQL query protocol
New query protocol `hierarchy.graphqls` is going to be added.
```graphql
type HierarchyRelatedService {
  # The related service ID.
  id: ID!
  # The literal name of the #id.
  name: String!
  # The related service's Layer name.
  layer: String!
}

type HierarchyRelatedInstance {
  # The related instance ID.
  id: ID!
  # The literal name of the #id. Instance Name.
  name: String!
  # The related instance service's Layer name.
  layer: String!
}

type HierarchyServiceRelation {
  upperService: HierarchyRelatedService!
  lowerService: HierarchyRelatedService!
}

type HierarchyInstanceRelation {
  upperInstance: HierarchyRelatedInstance!
  lowerInstance: HierarchyRelatedInstance!
}

type ServiceHierarchy {
  relations: [HierarchyServiceRelation!]!
}

type InstanceHierarchy {
  relations: [HierarchyInstanceRelation!]!
}

type LayerLevel {
  # The layer name.
  layer: String!
  # The layer level.
  # The level of the upper service should greater than the level of the lower service.
  level: Int!
}

extend type Query {
  # Query the service hierarchy, based on the given service. Will recursively return all related layers services in the hierarchy.
  getServiceHierarchy(serviceId: ID!, layer: String!): ServiceHierarchy!
  # Query the instance hierarchy, based on the given instance. Will return all direct related layers instances in the hierarchy, no recursive.
  getInstanceHierarchy(instanceId: ID!, layer: String!): InstanceHierarchy!
  # List layer hierarchy levels. The layer levels are defined in the `hierarchy-definition.yml`.
  listLayerLevels: [LayerLevel!]!
}
```

## New data models
- service_hierarchy_relation

  | Column name           | Data type | Description                                                 |
  |-----------------------|-----------|-------------------------------------------------------------|
  | id                    | String    | serviceId.servicelayer-relatedServiceId.relatedServiceLayer |
  | service_id            | String    | upper service id                                            |
  | service_layer         | int       | upper service layer value                                   |
  | related_service_id    | String    | lower service id                                            |
  | related_service_layer | int       | lower service layer value                                   |
  | time_bucket           | long      |                                                             |

- instance_hierarchy_relation

  | Column name           | Data type | Description                                                  |
  |-----------------------|-----------|--------------------------------------------------------------|
  | id                    | String    | instanceId.servicelayer-relateInstanceId.relatedServiceLayer |
  | instance_id           | String    | upper instance id                                            |
  | service_layer         | int       | upper service layer value                                    |
  | related_instance_id   | String    | lower instance id                                            |
  | related_service_layer | int       | lower service layer value                                    |
  | time_bucket           | long      |                                                              |

## Internal APIs
Internal APIs should be exposed in the Core module to support building the hierarchy relationship.
```java
public void toServiceHierarchyRelation(String upperServiceName, Layer upperServiceLayer, String lowerServiceName, Layer lowerServiceLayer);
public void toInstanceHierarchyRelation(String upperInstanceName, String upperServiceName, Layer upperServiceLayer, String lowerInstanceName, String lowerServiceName, Layer lowerServiceLayer);
```

## Hierarchy Definition
All layers hierarchy relations are defined in the `hierarchy-definition.yml` file. 
OAP will check the hierarchy relations before building and use the matching rules to auto match the relations. Here is an example:

```yaml
# Define the hierarchy of service layers, the layers under the specific layer are related lower of the layer.
# The relation could have a matching rule for auto matching, which are defined in the `auto-matching-rules` section.
# All the layers are defined in the file `org.apache.skywalking.oap.server.core.analysis.Layers.java`.

hierarchy:
  MESH:
    MESH_DP: name
    K8S_SERVICE: short-name

  MESH_DP:
    K8S_SERVICE: short-name

  GENERAL:
    K8S_SERVICE: lower-short-name-remove-ns

  MYSQL:
    K8S_SERVICE: ~

  VIRTUAL_DATABASE:
    MYSQL: ~

# Use Groovy script to define the matching rules, the input parameters are the upper service(u) and the lower service(l) and the return value is a boolean.
# which are used to match the relation between the upper service(u) and the lower service(l) on the different layers.
auto-matching-rules:
  # the name of the upper service is equal to the name of the lower service
  name: "{ (u, l) -> u.name == l.name }"
  # the short name of the upper service is equal to the short name of the lower service
  short-name: "{ (u, l) -> u.shortName == l.shortName }"
  # remove the namespace from the lower service short name
  lower-short-name-remove-ns: "{ (u, l) -> u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')) }"

layer-levels:
  # The hierarchy level of the service layer, the level is used to define the order of the service layer for UI presentation.
  # The level of the upper service should greater than the level of the lower service in `hierarchy` section.
  MESH: 3
  GENERAL: 3
  VIRTUAL_DATABASE: 3
  MYSQL: 2
  MESH_DP: 1
  K8S_SERVICE: 0
```

## General usage docs

This proposal doesn't impact the end user in any way of using SkyWalking. The remarkable change will be in the UI. On
the service dashboard and topology map, the user should be able to see the hierarchy relationship, which means other
services in other layers are logically the same as the current one. UI would provide the link to jump to the relative
service's dashboard.

## No Goal

This proposal doesn't cover all the logic about how to detect the service hierarchy structure. All those should be in a
separate SWIP.
