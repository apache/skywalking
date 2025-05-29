# Get Node List in the Cluster

The OAP cluster is a set of OAP servers that work together to provide a scalable and reliable service. The OAP cluster
supports [various cluster coordinator](../setup/backend/backend-cluster.md) to manage the cluster membership and the
communication.
This API provides capability to query the node list in the cluster from every OAP node perspective. If the cluster
coordinator doesn't work properly, the node list may be incomplete or incorrect. So, we recommend you to check the
node list when set up a cluster.

This API is used to get the unified and effective TTL configurations.

- URL, `http://{core restHost}:{core restPort}/status/cluster/nodes`
- HTTP GET method.

```json
{
  "nodes": [
    {
      "host": "10.0.12.23",
      "port": 11800,
      "self": true
    },
    {
      "host": "10.0.12.25",
      "port": 11800,
      "self": false
    },
    {
      "host": "10.0.12.37",
      "port": 11800,
      "self": false
    }
  ]
}
```

The `nodes` list all the nodes in the cluster. The size of the list should be exactly same as your cluster setup.
The `host` and `port` are the address of the OAP node, which are used for OAP nodes communicating with each other. The
`self` is a flag to indicate whether the node is the current node, others are remote nodes.
