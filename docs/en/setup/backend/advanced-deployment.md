# Advanced deployment
OAP servers inter communicate with each other in a cluster environment. 
In the cluster mode, you could run in different roles.
- Mixed(default)
- Receiver
- Aggregator

In some time, users want to deploy cluster nodes with explicit role. Then could use this.

## Mixed
Default role, the OAP should take responsibilities of
1. Receive agent traces or metrics.
1. Do L1 aggregation
1. Internal communication(send/receive)
1. Do L2 aggregation
1. Persistence
1. Alarm

## Receiver
The OAP should take responsibilities of
1. Receive agent traces or metrics.
1. Do L1 aggregation
1. Internal communication(send)

## Aggregator
The OAP should take responsibilities of
1. Internal communication(receive)
1. Do L2 aggregation
1. Persistence
1. Alarm

___
These roles are designed for complex deployment requirements based on security and network policy.

## Kubernetes
If you are using our native [Kubernetes coordinator](backend-cluster.md#kubernetes), the `labelSelector`
setting is used for `Aggregator` choose rules. Choose the right OAP deployment based on your requirements.