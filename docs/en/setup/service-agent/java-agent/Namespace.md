# Namespace

## Background
SkyWalking is a monitoring tool, which collects metrics from a distributed system. In the real world, a very large distributed system
includes hundreds of services, thousands of service instances. In that case, most likely, more than one group, 
even more than one company are maintaining and monitoring the distributed system. Each one of them takes charge of different parts,
don't want or shouldn't share there metrics.

Namespace is the proposal from this.It is used for tracing and monitoring isolation.

## Set the namespace
### Set agent.namespace in agent config
```properties
# The agent namespace
# agent.namespace=default-namespace
``` 

The default value of `agent.namespace` is empty. 

**Influence**
The default header key of SkyWalking is `sw6`, more in this [document](../../../protocols/Skywalking-Cross-Process-Propagation-Headers-Protocol-v2.md).
After `agent.namespace` is set, the key changes to `namespace-sw6`.

The across process propagation chain breaks, when the two sides are using different namespace.
