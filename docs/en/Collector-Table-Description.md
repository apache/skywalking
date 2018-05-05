# Collector Table Description
This document describes the usage of tables and their columns, based on elasticsearch storage implementation.

## Metric table time bucket
### Date format
- second: `yyyyMMddHHmmss`
- minute: `yyyyMMddHHmm`
- hour: `yyyyMMddHH`
- day: `yyyyMMdd`
- month: `yyyyMM`

## Tables of Register related
### Application
- Table name: application
- Get or create a database record by "application_code". 

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value same as application_id
application_code | ac | Keyword | The application name, see `agent.config`
application_id | ai | Integer | Auto increment, is a signed integer
is_address | ia | Integer | Is a boolean data. True(1), False(0)
address_id | ni | Integer | A foreign key reference by network_address table

- Column `is_address`
  - `false`. A real application, which has a custom `application_code`. At the same time, the `address_id` column value must to be 0. 
  - `true`. A conjunction application based on IP address. `address_id` is registered in `network_address` table.

### Instance
- Table name: instance
- Create a instance by `application_id` and `agent_uuid`

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value same as instance_id
application_id | ai | Integer | Owner application id
application_code | ac | Text | Owner application code
agent_uuid | iau | Keyword | Uniquely identifies each server monitored by agent
register_time | irt | Long | First register time
instance_id | ii | Integer | Auto increment, is a unsigned integer
heartbeat_time | iht | Long | Represent server is alive 
os_info | ioi | Text | A Json data.
is_address | iia | Integer | Is a boolean data. True(1), False(0)
address_id | ni | Integer | A foreign key reference by network_address table

- Column `os_info` 
  - For example: {"osName":"MacOS X","hostName":peng-yongsheng","processId":1000,"ipv4s":["10.0.0.1","10.0.0.2"]}
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
_id | _id | Keyword | primary key, es speciality, the value same as address_id
address_id | ni | Integer | Auto increment, is a signed integer
network_address | na | Keyword | Host name or IP address
src_span_layer | ssl | Integer | Register by client or server side
server_type | st | Integer | Such as component id, used for topology. 

### ServiceName
- Table name: service_name
- Create a service record by "service_name_keyword", "application_id" and "src_span_type". 

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value same as service_id
service_id | si | Integer | Auto increment, is a signed integer
service_name | sn | Text | Operation name, used for fuzzy matching
service_name_keyword | snk | Keyword | Operation name, used for full matching
application_id | ai | Integer | Owner application id
src_span_type | sst | Integer | Register from client or server side based on `src_span_type`

- See `src_span_type` in [protocol doc](Trace-Data-Protocol.md#network-address-register-service)

## Table of Trace Metric related
### ApplicationComponent
- Table name: application_component_`TimeDimension`
- TimeDimension contains minute, hour, day, month
- It is primarily used for the view of node type in application topology.

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `application_id`_`component_id`
component_id | ci | Integer | [Component id](../../apm-protocol/apm-network/src/main/java/org/apache/skywalking/apm/network/trace/component/ComponentsDefine.java)
application_id | ai | Integer | Owner application id
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### ApplicationMapping
- Table name: application_mapping_`TimeDimension`
- TimeDimension contains minute, hour, day, month
- For example: Application A calls Application B, collector generates the following two metrics::
    * From the caller's trace data: A application -> B application's IP address (Topology will use this metric when B application is not monitored by agent)
    * From the callee's trace data: A application -> B application (Topology will use this metric when B application is monitored by agent)

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `application_id`_`mapping_application_id`
application_id | ai | Integer | Registered at server side.
mapping_application_id | mai | Integer | Registered at client side with the server's IP address.
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### ApplicationMetric
- Table name: application_metric_`TimeDimension`
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `application_id`_`source_value`
application_id | ai | Integer | Owner application id
source_value | sv | Integer | Caller(0), Callee(1)
transaction_calls | t1 | Long | The total number of calls based on `time_bucket`
transaction_error_calls | t2 | Long | The total number of error calls, sums values aggregate by `time_bucket`
transaction_duration_sum | t3 | Long | The total duration of calls based on `time_bucket`
transaction_error_duration_sum | t4 | Long | The total duration of error calls based on `time_bucket`
transaction_average_duration | t5 | Long | The average duration of all calls, used for order by this column in database.
business_transaction_calls | b1 | Long | 
business_transaction_error_calls | b2 | Long | 
business_transaction_duration_sum | b3 | Long | 
business_transaction_error_duration_sum | b4 | Long | 
business_transaction_average_duration | b5 | Long | 
mq_transaction_calls | m1 | Long | 
mq_transaction_error_calls | m2 | Long | 
mq_transaction_duration_sum | m3 | Long | 
mq_transaction_error_duration_sum | m4 | Long | 
mq_transaction_average_duration | m5 | Long | 
satisfied_count | a1 | Long | [The formula](../../apm-collector/apm-collector-core/src/main/java/org/apache/skywalking/apm/collector/core/util/ApdexThresholdUtils.java)
tolerating_count | a2 | Long | 
frustrated_count | a3 | Long | 
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### ApplicationReferenceMetric
- Table name: application_reference_metric_`TimeDimension`
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `front_application_id`_`behind_application_id`_`source_value`
front_application_id | fai | Integer | 
behind_application_id | bai | Integer | 
source_value | sv | Integer | Caller(0), Callee(1)
transaction_calls | t1 | Long | The total number of calls based on `time_bucket`
transaction_error_calls | t2 | Long | The total number of error calls, sums values aggregate by `time_bucket`
transaction_duration_sum | t3 | Long | The total duration of calls based on `time_bucket`
transaction_error_duration_sum | t4 | Long | The total duration of error calls based on `time_bucket`
transaction_average_duration | t5 | Long | The average duration of all calls, used for order by this column in database.
business_transaction_calls | b1 | Long | 
business_transaction_error_calls | b2 | Long | 
business_transaction_duration_sum | b3 | Long | 
business_transaction_error_duration_sum | b4 | Long | 
business_transaction_average_duration | b5 | Long | 
mq_transaction_calls | m1 | Long | 
mq_transaction_error_calls | m2 | Long | 
mq_transaction_duration_sum | m3 | Long | 
mq_transaction_error_duration_sum | m4 | Long | 
mq_transaction_average_duration | m5 | Long | 
satisfied_count | a1 | Long | [The formula](../../apm-collector/apm-collector-core/src/main/java/org/apache/skywalking/apm/collector/core/util/ApdexThresholdUtils.java)
tolerating_count | a2 | Long | 
frustrated_count | a3 | Long | 
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### InstanceMapping
- Table name: instance_mapping_`TimeDimension`
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `instance_id`_`address_id`
application_id | ai | Integer | The `instance_id`'s owner application id.
instance_id | ii | Integer | Registered at server side.
address_id | ni | Integer | Registered at client side with the server's IP address.
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### InstanceMetric
- Table name: instance_metric_`TimeDimension`
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `instance_id`_`source_value`
application_id | ai | Integer | 
instance_id | ii | Integer | 
source_value | sv | Integer | Caller(0), Callee(1)
transaction_calls | t1 | Long | The total number of calls based on `time_bucket`
transaction_error_calls | t2 | Long | The total number of error calls, sums values aggregate by `time_bucket`
transaction_duration_sum | t3 | Long | The total duration of calls based on `time_bucket`
transaction_error_duration_sum | t4 | Long | The total duration of error calls based on `time_bucket`
transaction_average_duration | t5 | Long | The average duration of all calls, used for order by this column in database.
business_transaction_calls | b1 | Long | 
business_transaction_error_calls | b2 | Long | 
business_transaction_duration_sum | b3 | Long | 
business_transaction_error_duration_sum | b4 | Long | 
business_transaction_average_duration | b5 | Long | 
mq_transaction_calls | m1 | Long | 
mq_transaction_error_calls | m2 | Long | 
mq_transaction_duration_sum | m3 | Long | 
mq_transaction_error_duration_sum | m4 | Long | 
mq_transaction_average_duration | m5 | Long | 
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### InstanceReferenceMetric
- Table name: instance_reference_metric_`TimeDimension`
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `front_instance_id`_`behind_instance_id`_`source_value`
front_application_id | fai | Integer | 
behind_application_id | bai | Integer | 
front_instance_id | fii | Integer | 
behind_instance_id | bii | Integer | 
source_value | sv | Integer | Caller(0), Callee(1)
transaction_calls | t1 | Long | The total number of calls based on `time_bucket`
transaction_error_calls | t2 | Long | The total number of error calls, sums values aggregate by `time_bucket`
transaction_duration_sum | t3 | Long | The total duration of calls based on `time_bucket`
transaction_error_duration_sum | t4 | Long | The total duration of error calls based on `time_bucket`
transaction_average_duration | t5 | Long | The average duration of all calls, used for order by this column in database.
business_transaction_calls | b1 | Long | 
business_transaction_error_calls | b2 | Long | 
business_transaction_duration_sum | b3 | Long | 
business_transaction_error_duration_sum | b4 | Long | 
business_transaction_average_duration | b5 | Long | 
mq_transaction_calls | m1 | Long | 
mq_transaction_error_calls | m2 | Long | 
mq_transaction_duration_sum | m3 | Long | 
mq_transaction_error_duration_sum | m4 | Long | 
mq_transaction_average_duration | m5 | Long | 
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### ServiceMetric
- Table name: service_metric_`TimeDimension`
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `service_id`_`source_value`
application_id | ai | Integer | 
instance_id | ii | Integer | 
service_id | si | Integer | 
source_value | sv | Integer | Caller(0), Callee(1)
transaction_calls | t1 | Long | The total number of calls based on `time_bucket`
transaction_error_calls | t2 | Long | The total number of error calls, sums values aggregate by `time_bucket`
transaction_duration_sum | t3 | Long | The total duration of calls based on `time_bucket`
transaction_error_duration_sum | t4 | Long | The total duration of error calls based on `time_bucket`
transaction_average_duration | t5 | Long | The average duration of all calls, used for order by this column in database.
business_transaction_calls | b1 | Long | 
business_transaction_error_calls | b2 | Long | 
business_transaction_duration_sum | b3 | Long | 
business_transaction_error_duration_sum | b4 | Long | 
business_transaction_average_duration | b5 | Long | 
mq_transaction_calls | m1 | Long | 
mq_transaction_error_calls | m2 | Long | 
mq_transaction_duration_sum | m3 | Long | 
mq_transaction_error_duration_sum | m4 | Long | 
mq_transaction_average_duration | m5 | Long | 
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### ServiceReferenceMetric
- Table name: service_reference_metric_`TimeDimension`
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `time_bucket`_`metric_id`
metric_id | mi | Keyword | `front_service_id`_`behind_service_id`_`source_value`
front_application_id | fai | Integer | 
front_instance_id | fii | Integer | 
front_service_id | fsi | Integer | 
behind_application_id | bai | Integer | 
behind_instance_id | bii | Integer | 
behind_service_id | bsi | Integer | 
source_value | sv | Integer | Caller(0), Callee(1)
transaction_calls | t1 | Long | The total number of calls based on `time_bucket`
transaction_error_calls | t2 | Long | The total number of error calls, sums values aggregate by `time_bucket`
transaction_duration_sum | t3 | Long | The total duration of calls based on `time_bucket`
transaction_error_duration_sum | t4 | Long | The total duration of error calls based on `time_bucket`
transaction_average_duration | t5 | Long | The average duration of all calls, used for order by this column in database.
business_transaction_calls | b1 | Long | 
business_transaction_error_calls | b2 | Long | 
business_transaction_duration_sum | b3 | Long | 
business_transaction_error_duration_sum | b4 | Long | 
business_transaction_average_duration | b5 | Long | 
mq_transaction_calls | m1 | Long | 
mq_transaction_error_calls | m2 | Long | 
mq_transaction_duration_sum | m3 | Long | 
mq_transaction_error_duration_sum | m4 | Long | 
mq_transaction_average_duration | m5 | Long | 
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### GlobalTrace
- Table name: global_trace
- The relationship between trace id and segment id is many to many.

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `global_trace_id`_`segment_id`
segment_id | sgi | Keyword | 
trace_id | ti | Keyword | 
time_bucket | tb | Long | Second date format, [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### SegmentDuration
- Table name: segment_duration
- The relationship between trace id and segment id is many to many.

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, `global_trace_id`_`segment_id`
segment_id | sgi | Keyword | 
trace_id | ti | Keyword | 
application_id | ai | Integer | Owner of the segment
service_name | sn | Text | The entry span's operation name in this segment
duration | ddt | Long | The cost duration of this segment
start_time | dst | Long | 
end_time | det | Long | 
is_error | die | Long | Is a boolean data. True(1), False(0)
time_bucket | tb | Long | Second date format, [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### Segment
- Table name: segment

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value same as `segment_id`
data_binary | sdb | Binary | The protobuf segment binary encode by base64 scheme.
time_bucket | tb | Long | Second date format, [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

## Tables of JVM Metric related
### CpuMetric
- Table name: cpu_metric_`TimeDimension`
- TimeDimension contains second, minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `time_bucket`_`metric_id`
metric_id | mi | Keyword | the value is `instance_id`
instance_id | ii | Integer | Owner instance id
usage_percent | up | Double | Cpu usage percent, sums values aggregate by `time_bucket`
times | t | Long | The records received times in this time bucket
time_bucket | tb | Long | [Date format](Collector-Table-Description.md#Metric-table-time-bucket)

### GCMetric
- Table name: gc_metric_`TimeDimension`
- TimeDimension contains second, minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `time_bucket`_`metric_id`
metric_id | mi | Keyword | the value is `instance_id`_`phrase`
instance_id | ii | Integer | Owner instance id
phrase | p | Integer | [GCPhrase](https://github.com/apache/incubator-skywalking-data-collect-protocol/blob/master/JVMMetricsService.proto#L80-L83)
count | c | Long | GC count, sums values aggregate by `time_bucket`
times | t | Long | The records received times in this time bucket
duration | d | Long | GC duration count,  sums values aggregate by `time_bucket`
time_bucket | tb | Long | [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### MemoryMetric
- Table name: memory_metric_`TimeDimension`
- TimeDimension contains second, minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `time_bucket`_`metric_id`
metric_id | mi | Keyword | the value is `instance_id`_`is_heap`
instance_id | ii | Integer | Owner instance id
is_heap | ih | Integer | Is a boolean data. True(1), False(0)
init | init | Long | Sums values aggregate by `time_bucket`
max | max | Long | Sums values aggregate by `time_bucket`
used | used | Long | Sums values aggregate by `time_bucket`
committed | cd | Long | Sums values aggregate by `time_bucket`
times | t | Long | The records received times in this time bucket
time_bucket | tb | Long | [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### MemoryPoolMetric
- Table name: memory_pool_metric_`TimeDimension`
- TimeDimension contains second, minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `time_bucket`_`metric_id`
metric_id | mi | Keyword | the value is `instance_id`_`pool_type`
instance_id | ii | Integer | Owner instance id
pool_type | pt | Integer | Owner instance id
init | init | Long | Sums values aggregate by `time_bucket`
max | max | Long | Sums values aggregate by `time_bucket`
used | used | Long | Sums values aggregate by `time_bucket`
committed | ct | Long | Sums values aggregate by `time_bucket`
times | t | Long | The records received times in this time bucket
time_bucket | tb | Long | [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

## Table of Alarm metric related
### ApplicationAlarm
- Table name: application_alarm

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`application_id`
application_id | ai | Integer | Owner application id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
last_time_bucket | ltb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### ApplicationAlarmList
- Table name: application_alarm_list
- TimeDimension contains minute, hour, day, month

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `time_bucket`_`metric_id`
metric_id | mi | Keyword | `source_value`_`alarm_type`_`application_id`
alarm_content | aac | Text | 
application_id | ai | Integer | Owner application id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
time_bucket | tb | Long | [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### ApplicationReferenceAlarm
- Table name: application_reference_alarm

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`application_id`
front_application_id | fai | Integer | Owner application id
behind_application_id | bai | Integer | Owner application id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
last_time_bucket | ltb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### ApplicationReferenceAlarmList
- Table name: application_reference_alarm

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`application_id`
front_application_id | fai | Integer | Owner application id
behind_application_id | bai | Integer | Owner application id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
time_bucket | tb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### InstanceAlarm
- Table name: application_alarm

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`instance_id`
application_id | ai | Integer | Owner application id
instance_id | ii | Integer | Owner instance id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
last_time_bucket | ltb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### InstanceAlarmList
- Table name: instance_alarm_list

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`instance_id`
application_id | ai | Integer | Owner application id
instance_id | ii | Integer | Owner instance id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
time_bucket | tb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### InstanceReferenceAlarm
- Table name: instance_reference_alarm

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`instance_id`
front_application_id | fai | Integer | Owner application id
front_instance_id | fii | Integer | Owner instance id
behind_application_id | bai | Integer | Owner instance id
behind_instance_id | bii | Integer | Owner instance id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
last_time_bucket | ltb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### InstanceReferenceAlarmList
- Table name: instance_reference_alarm_list

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`instance_id`
front_application_id | fai | Integer | Owner application id
front_instance_id | fii | Integer | Owner instance id
behind_application_id | bai | Integer | Owner instance id
behind_instance_id | bii | Integer | Owner instance id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
time_bucket | tb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### ServiceAlarm
- Table name: service_alarm

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`service_id`
application_id | ai | Integer | Owner application id
instance_id | ii | Integer | Owner instance id
service_id | si | Integer | Owner service id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
last_time_bucket | ltb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### ServiceAlarmList
- Table name: service_alarm_list

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`service_id`
application_id | ai | Integer | Owner application id
instance_id | ii | Integer | Owner instance id
service_id | si | Integer | Owner instance id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
time_bucket | tb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### ServiceReferenceAlarm
- Table name: service_reference_alarm

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `source_value`_`alarm_type`_`service_id`
front_application_id | fai | Integer | Owner application id
front_instance_id | fii | Integer | Owner instance id
front_service_id | fsi | Integer | Owner service id
behind_application_id | bai | Integer | Owner service id
behind_instance_id | bii | Integer | Owner service id
behind_service_id | bsi | Integer | Owner service id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
last_time_bucket | ltb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)

### ServiceReferenceAlarmList
- Table name: service_reference_alarm_list

Column Name | Short Name | Data Type | Description
----------- | ---------- | --------- | ---------
_id | _id | Keyword | primary key, es speciality, the value is `time_bucket`_`source_value`_`alarm_type`_`service_id`
front_application_id | fai | Integer | Owner application id
front_instance_id | fii | Integer | Owner instance id
front_service_id | fsi | Integer | Owner service id
behind_application_id | bai | Integer | Owner service id
behind_instance_id | bii | Integer | Owner service id
behind_service_id | bsi | Integer | Owner service id
source_value | sv | Integer | Caller(0), Callee(1)
alarm_type | aat | Integer | ERROR_RATE(0), SLOW_RTT(1)
alarm_content | aac | Text | 
time_bucket | tb | Long | Second date format. [A formatted date](Collector-Table-Description.md#Metric-table-time-bucket)
