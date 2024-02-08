# Support available layers of service in the topology.

## Motivation

UI could jump to the service dashboard and query service hierarchy from the topology node. 
For now topology node includes name and ID but without layer, as the service could have multiple layers,
the limitation is that it is only works on the current layer which the topology represents:
1. UI could not jump into another layer's dashboard of the service.
2. UI could not query the service hierarchy from the topology node if the node is not in current layer.

Here are typical use cases:
should have a chance to jump into another layer's dashboard of the service:
1. In the mesh topology, mesh(layer MESH) and mesh-dp(layer MESH_DP) share a similar topology, one node will have two layers.
2. In the mesh topology, agent(layer GENERAL) + virtual database(layer VIRTUAL_DATABASE), the node is in different layers.

Both of these two cases have hybrid layer topology. If we could support that, we could have a better x-layer interaction.

## Architecture Graph

There is no significant architecture-level change.

## Propose Changes

Add the layers info into topology node:
1. When building the topology node fetch the layers info from the service according to the service id.
2. Return `layers` info in the `Node` when query the topology.

## Imported Dependencies libs and their licenses.

No new library is planned to be added to the codebase.

## Compatibility

About the **protocol**, there should be no breaking changes, but enhancements only. New field `layers` is going to be added to the
`Node` in the query protocol `topology.graphqls`.

```graphql
type Node {
  # The service ID of the node.
  id: ID!
  # The literal name of the #id.
  name: String!
  # The type name may be
  # 1. The service provider/middleware tech, such as: Tomcat, SpringMVC
  # 2. Conjectural Service, e.g. MySQL, Redis, Kafka
  type: String
  # It is a conjecture node or real node, to represent a service or endpoint.
  isReal: Boolean!
  # The layers of the service.
  layers: [String!]!
}
```

## General usage docs

This proposal doesn't impact the end user in any way of using SkyWalking. The remarkable change will be in the UI topology map, 
users could jump into the proper layer's service dashboard and query the service hierarchy from the topology node.
