# Backend Load Balancer

When set the Agent or Envoy connecting to OAP server directly as in default, OAP server cluster would face the problem
of OAP load imbalance. This issue would be very serious in high traffic load scenarios. Satellite is recommended to be
used as a native gateway proxy, to provide load balancing capabilities for data content before the data from Agent/Envoy
reaches the OAP. The major difference between Satellite and other general wide used proxy(s), like Envoy, is that,
Satellite would route the data accordingly to contents rather than connection, as gRPC streaming is used widely in
SkyWalking.

Follow instructions in the [Setup SkyWalking Satellite](https://skywalking.apache.org/docs/#SkyWalkingSatellite)
to deploy Satellite and connect your application to the satellite.

[Scaling with Apache SkyWalking](https://skywalking.apache.org/blog/2022-01-24-scaling-with-apache-skywalking/) blog
introduces the theory and technology details how to set up load balancer for the OAP cluster.