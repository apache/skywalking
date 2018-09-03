# Choose receiver
Receiver is a concept in SkyWalking backend. All modules, which are responsible for receiving telemetry
or tracing data from other being monitored system, are all being called **Receiver**. Although today, most of 
receivers are using gRPC or HTTPRestful to provide service, actually, whether listening mode or pull mode
could be receiver. Such as a receiver could base on pull data from remote, like Kakfa MQ.

We have following receivers
1. 