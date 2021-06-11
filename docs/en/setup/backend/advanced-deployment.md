# Advanced deployment
OAP servers communicate with each other in a cluster environment. 
In the cluster mode, you could run in different roles.
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
1. Internal communication(receive)
1. L2 aggregation
1. Persistence
1. Alarm

___
These roles are designed for complex deployment requirements on security and network policy.

## Kubernetes
If you are using our native [Kubernetes coordinator](backend-cluster.md#kubernetes), the `labelSelector`
setting is used for `Aggregator` role selection rules. Choose the right OAP deployment based on your needs.
