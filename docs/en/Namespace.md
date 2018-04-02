# Namespace
## Supported version
5.0.0-beta +

## Background
SkyWalking is a monitoring tool, which collects metrics from a distributed system. In the real world, a very large distributed system
includes hundreds of application, thousands of application instance. In that case, more likely, more than one group, 
even than one company are maintaining and monitoring the distributed system. Each one of them takes charge of different parts,
don't or can't share there metrics.

Namespace is proposal from this.It is used for tracing and monitoring isolation.

## Set the namespace
### Set agent.namespace in agent config
```properties
# The agent namespace
# agent.namespace=default-namespace
``` 

The default value of `agent.namespace` is empty. 

**Influence**
The default header key of SkyWalking is `sw3`, more in this [document](Skywalking-Cross-Process-Propagation-Headers-Protocol-v1.md).
After `agent.namespace` set, the key changes to `namespace:sw3`.

The across process propagation chain breaks, when the two sides are using different namespace.

### Set namespace in collector
```yml
configuration:
  default:
    namespace: xxxxx
```

**Influences**
1. If cluster model is active, with zookeeper implementation, The path in zookeeper is changed to include namespace prefix path.
1. If use Elasticsearch as storage implementation, all type names are changed to include namespace prefix.


