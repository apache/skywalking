# Collector Table Description
This document describes the usage of tables and their columns, based on elasticsearch storage implementation.

## Tables of Register related
### Application
- Table name: application
- Get or create a database record by "application_code". 

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
application_code | c1 | Keyword | The application name, see `agent.config`
application_id | c2 | Integer | Auto increment, is a signed integer
layer | c3 | Integer | Register by client or server side
is_address | c4 | Integer | Is a boolean data. True(1), False(0)
address_id | c5 | Integer | A foreign key reference by network_address table

- Columen `is_address`
  - `false`. A real application, which has a custom `application_code`. At the same time, the `address_id` column value must to be 0. 
  - `true`. A conjuction application based on IP address. `address_id` is registered in `network_address` table.

### Instance
- Table name: instance
- Create a instance by `application_id` and `agent_uuid`

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
application_id | c1 | Integer | Owner application id
application_code | c2 | Text | Owner application code
agent_uuid | c3 | Keyword | Uniquely identifies each server monitored by agent
register_time | c4 | Long | First register time
instance_id | c5 | Integer | Auto increment, is a unsigned integer
heartbeat_time | c6 | Long | Represent server is alive 
os_info | c7 | Text | A Json data.
is_address | c8 | Integer | Is a boolean data. True(1), False(0)
address_id | c9 | Integer | A foreign key reference by network_address table

- Column `os_info` 
  - For example: {"osName":"MacOS X","hostName":"peng-yongsheng","processId":1000,"ipv4s":["10.0.0.1","10.0.0.2"]}
- Column `heartbeat_time`
  - Updated by agent heart beat [1]
  - Updated by JVM metric data [2]
  - Updated by trace segment data. [3]
  - Priority: [1] > [2] > [3]

### NetworkAddress
- Table name: network_address
- Create a network address record by "network_address" and "span_layer". 

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
address_id | c1 | Integer | Auto increment, is a signed integer
network_address | c2 | Keyword | Host name or IP address
span_layer | c3 | Integer | Register by client or server side
server_type | c4 | Integer | Such as component id, used for topology. 

### ServiceName
- Table name: service_name
- Create a service record by "service_name_keyword", "application_id" and "src_span_type". 

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
service_id | c1 | Integer | Auto increment, is a signed integer
service_name | c2 | Text | Operation name, used for fuzzy matching
service_name_keyword | c3 | Keyword | Operation name, used for full matching
application_id | c4 | Integer | Owner application id
src_span_type | c5 | Integer | Register from client or server side based on `src_span_type`

- See `src_span_type` in [protocol doc](Trace-Data-Protocol.md#network-address-register-service)

## Table For Metric

## Table For JVM

## Table For Alarm
