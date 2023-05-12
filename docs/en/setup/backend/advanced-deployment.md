# Advanced deployment
OAP servers communicate with each other in a cluster environment to do distributed aggregation.
In the cluster mode, all OAP nodes are running in Mixed mode by default.

The available roles for OAP are,
- Mixed(default)
- Receiver
- Aggregator

Sometimes users may wish to deploy cluster nodes with a clearly defined role. They could then use this function.

## Mixed
By default, the OAP is responsible for:
1. Receiving agent traces or metrics.
1. L1 aggregation
1. Internal communication (sending/receiving)
1. L2 aggregation
1. Persistence
1. Alarm

## Receiver
The OAP is responsible for:
1. Receiving agent traces or metrics.
1. L1 aggregation
1. Internal communication (sending)

## Aggregator
The OAP is responsible for:
1. Internal communication(receiving from Receiver and Mixed roles OAP)
1. L2 aggregation
1. Persistence
1. Alarm

___
These roles are designed for complex deployment requirements on security and network policy.

## Kubernetes
If you are using our native [Kubernetes coordinator](backend-cluster.md#kubernetes), and you insist to install OAP nodes
with a clearly defined role. There should be two deployments for each role,
one for receiver OAPs and the other for aggregator OAPs to separate different system environment settings. 
Then, the `labelSelector` should be set for `Aggregator` role selection rules to choose the right OAP deployment based on your needs.
