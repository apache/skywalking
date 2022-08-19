# Backend Load Balancer

When setting the Agent or Envoy to connect to the OAP server directly as in default, the OAP server cluster would face the problem
of load imbalance. This issue would be severe in high-traffic load scenarios. [SkyWalking Satellite](https://github.com/apache/skywalking-satellite) is recommended to be
used as a native gateway proxy to provide load balancing capabilities for data content before the data from Agent/ Envoy
reaches the OAP. The major difference between Satellite and other general wide used proxy(s), like Envoy, is that it would route the data accordingly to contents rather than connection, as gRPC streaming is used widely in SkyWalking.

Follow instructions in the [Setup SkyWalking Satellite](https://skywalking.apache.org/docs/#SkyWalkingSatellite)
to deploy Satellite and connect your application to the Satellite.

[Scaling with Apache SkyWalking](https://skywalking.apache.org/blog/2022-01-24-scaling-with-apache-skywalking/) blog
introduces the theory and technology details on how to set up a load balancer for the OAP cluster.