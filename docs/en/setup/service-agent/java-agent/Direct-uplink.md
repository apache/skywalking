# Direct uplink
## Supported version
5.0.0-beta +

## What is direct uplink?
In default, SkyWalking agent uses naming service to find all collector addresses. Then connect to gRPC services.

In **direct uplink**, mean no naming service available or don't work well, set the gRPC addresses in agent directly. 
The agent uses theses addresses to connect gRPC services.

## Why need this?
Agent uplink data through the following network
1. VPCs in Cloud
1. Internet
1. Different subnet.
1. IPs and Ports proxy

## Set the agent config
1. Remove `collector.servers` config item.
2. You can find the following settings in `agent.config`
```
# Collector agent_gRPC/grpc service addresses.
# Secondary address setting, only effect when "collector.servers" is empty.
# By using this, no discovery mechanism provided. The agent only uses these addresses to uplink data.
# Recommend to use this only when collector cluster IPs are unreachable from agent side. Such as:
#   1. Agent and collector cluster are in different VPC in Cloud.
#   2. Agent uplinks data to collector cluster through Internet.
# collector.direct_servers=www.skywalking.service.io
```  

3. Set `collector.direct_servers` to a domain name, IP:PORTs, with split by comma(,).

