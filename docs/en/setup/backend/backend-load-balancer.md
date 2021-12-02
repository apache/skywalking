# Backend Load Balancer

At present, the Agent or Envoy service wants to exchange data with OAP, all of which are connected through direct connection, which will cause the problem of OAP load imbalance when exchanging large quantities.
Skywalking-Satellite can be used as a Gateway, providing load balancing capabilities for data content before the data from Agent/Envoy reaches the OAP.

Follow instructions in the [Setup SkyWalking Satellite](https://github.com/apache/skywalking-satellite)
to deploy Satellite and connect your application to the satellite.

Please read the Readme file.