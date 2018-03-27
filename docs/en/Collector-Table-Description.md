# Collector Table Description
This document describe the usage of table and the means of table columns based on elastic search storage.

## Table For Register
### Application
- Table name: application
- Get or create a database record by "application_code". 
- Set the "is_address" column value to be false when it is a real application and the "address_id" column value must to be 0. 
- Set the value to be true when it is a ip address which used to call by client side and the "address_id" column value is the network address id which registered in the network_address table.

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
application_code | c1 | Keyword | The application name, see `agent.config`
application_id | c2 | Integer | Auto increment, is a signed integer
layer | c3 | Integer | Register by client or server side
is_address | c4 | Integer | Is a boolean data. True(1), False(0)
address_id | c5 | Integer | A foreign key reference by network_address table

### Instance
- Table name: instance
- Get or create a database record by "application_id" and "agent_uuid". 
- Agent will send heart beat time data to collector per second.
- Use the JVM metric data instead of heart beat time data when did not receive heart beat data.
- Use the trace metric data instead of heart beat time data when did not receive JVM metric data and heart beat data.
- Os_info sample: {"osName":"MacOS X","hostName":"peng-yongsheng","processId":1000,"ipv4s":["10.0.0.1","10.0.0.2"]}

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
application_id | c1 | Integer | The application id which this instance belongs to
application_code | c2 | Text | The application code which this instance belongs to
agent_uuid | c3 | Keyword | Uniquely identifies each server monitored by agent
register_time | c4 | Long | First register time
instance_id | c5 | Integer | Auto increment, is a unsigned integer
heartbeat_time | c6 | Long | Represent server is alive 
os_info | c7 | Text | A Json data.
is_address | c8 | Integer | Is a boolean data. True(1), False(0)
address_id | c9 | Integer | A foreign key reference by network_address table

### NetworkAddress
- Table name: network_address
- Get or create a database record by "network_address" and "span_layer". 

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
address_id | c1 | Integer | Auto increment, is a signed integer
network_address | c2 | Keyword | Host name or IP address
span_layer | c3 | Integer | Register by client or server side
server_type | c4 | Integer | Such as component id, used for topology. 

### ServiceName
- Table name: service_name
- Get or create a database record by "service_name_keyword" and "application_id" and "src_span_type". 

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
service_id | c1 | Integer | Auto increment, is a signed integer
service_name | c2 | Text | Operation name, used for fuzzy matching
service_name_keyword | c3 | Keyword | Operation name, used for full matching
application_id | c4 | Integer | The application id which this instance belongs to
src_span_type | c5 | Integer | Register by client or server side

## Table For Metric

## Table For JVM

## Table For Alarm