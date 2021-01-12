# Configuration Vocabulary
Configuration Vocabulary lists all available configurations provided by `application.yml`.

Module | Provider | Settings | Value(s) and Explanation | System Environment Variable¹ | Default |
----------- | ---------- | --------- | --------- |--------- |--------- |
core|default|role|Option values, `Mixed/Receiver/Aggregator`. **Receiver** mode OAP open the service to the agents, analysis and aggregate the results and forward the results for distributed aggregation. Aggregator mode OAP receives data from Mixer and Receiver role OAP nodes, and do 2nd level aggregation. **Mixer** means being Receiver and Aggregator both.|SW_CORE_ROLE|Mixed|
| - | - | restHost| Binding IP of restful service. Services include GraphQL query and HTTP data report|SW_CORE_REST_HOST|0.0.0.0|
| - | - | restPort | Binding port of restful service | SW_CORE_REST_PORT|12800|
| - | - | restContextPath| Web context path of restful service| SW_CORE_REST_CONTEXT_PATH|/|
| - | - | restMinThreads| Min threads number of restful service| SW_CORE_REST_JETTY_MIN_THREADS|1|
| - | - | restMaxThreads| Max threads number of restful service| SW_CORE_REST_JETTY_MAX_THREADS|200|
| - | - | restIdleTimeOut| Connector idle timeout in milliseconds of restful service| SW_CORE_REST_JETTY_IDLE_TIMEOUT|30000|
| - | - | restAcceptorPriorityDelta| Thread priority delta to give to acceptor threads of restful service| SW_CORE_REST_JETTY_DELTA|0|
| - | - | restAcceptQueueSize| ServerSocketChannel backlog  of restful service| SW_CORE_REST_JETTY_QUEUE_SIZE|0|
| - | - | gRPCHost|Binding IP of gRPC service. Services include gRPC data report and internal communication among OAP nodes|SW_CORE_GRPC_HOST|0.0.0.0|
| - | - | gRPCPort| Binding port of gRPC service | SW_CORE_GRPC_PORT|11800|
| - | - | gRPCSslEnabled| Activate SSL for gRPC service | SW_CORE_GRPC_SSL_ENABLED|false|
| - | - | gRPCSslKeyPath| The file path of gRPC SSL key| SW_CORE_GRPC_SSL_KEY_PATH| - |
| - | - | gRPCSslCertChainPath| The file path of gRPC SSL cert chain| SW_CORE_GRPC_SSL_CERT_CHAIN_PATH| - |
| - | - | gRPCSslTrustedCAPath| The file path of gRPC trusted CA| SW_CORE_GRPC_SSL_TRUSTED_CA_PATH| - |
| - | - | downsampling| The activated level of down sampling aggregation | | Hour,Day|
| - | - | enableDataKeeperExecutor|Controller of TTL scheduler. Once disabled, TTL wouldn't work.|SW_CORE_ENABLE_DATA_KEEPER_EXECUTOR|true|
| - | - | dataKeeperExecutePeriod|The execution period of TTL scheduler, unit is minute. Execution doesn't mean deleting data. The storage provider could override this, such as ElasticSearch storage.|SW_CORE_DATA_KEEPER_EXECUTE_PERIOD|5|
| - | - | recordDataTTL|The lifecycle of record data. Record data includes traces, top n sampled records, and logs. Unit is day. Minimal value is 2.|SW_CORE_RECORD_DATA_TTL|3|
| - | - | metricsDataTTL|The lifecycle of metrics data, including the metadata. Unit is day. Recommend metricsDataTTL >= recordDataTTL. Minimal value is 2.| SW_CORE_METRICS_DATA_TTL|7|
| - | - | enableDatabaseSession|Cache metrics data for 1 minute to reduce database queries, and if the OAP cluster changes within that minute.|SW_CORE_ENABLE_DATABASE_SESSION|true|
| - | - | topNReportPeriod|The execution period of top N sampler, which saves sampled data into the storage. Unit is minute|SW_CORE_TOPN_REPORT_PERIOD|10|
| - | - | activeExtraModelColumns|Append the names of entity, such as service name, into the metrics storage entities.|SW_CORE_ACTIVE_EXTRA_MODEL_COLUMNS|false|
| - | - | serviceNameMaxLength| Max length limitation of service name.|SW_SERVICE_NAME_MAX_LENGTH|70|
| - | - | instanceNameMaxLength| Max length limitation of service instance name. The max length of service + instance names should be less than 200.|SW_INSTANCE_NAME_MAX_LENGTH|70|
| - | - | endpointNameMaxLength| Max length limitation of endpoint name. The max length of service + endpoint names should be less than 240.|SW_ENDPOINT_NAME_MAX_LENGTH|150|
| - | - | searchableTracesTags | Define the set of span tag keys, which should be searchable through the GraphQL. Multiple values should be separated through the comma. | SW_SEARCHABLE_TAG_KEYS | http.method,status_code,db.type,db.instance,mq.queue,mq.topic,mq.broker|
| - | - | gRPCThreadPoolSize|Pool size of gRPC server| SW_CORE_GRPC_THREAD_POOL_SIZE | CPU core * 4|
| - | - | gRPCThreadPoolQueueSize| The queue size of gRPC server| SW_CORE_GRPC_POOL_QUEUE_SIZE | 10000|
| - | - | maxConcurrentCallsPerConnection | The maximum number of concurrent calls permitted for each incoming connection. Defaults to no limit. | SW_CORE_GRPC_MAX_CONCURRENT_CALL | - |
| - | - | maxMessageSize | Sets the maximum message size allowed to be received on the server. Empty means 4 MiB | SW_CORE_GRPC_MAX_MESSAGE_SIZE | 4M(based on Netty) |
| - | - | remoteTimeout |Timeout for cluster internal communication, in seconds.| - |20|
| - | - | maxSizeOfNetworkAddressAlias|Max size of network address detected in the be monitored system.| - | 1_000_000|
| - | - | maxPageSizeOfQueryProfileSnapshot|The max size in every OAP query for snapshot analysis| - | 500 |
| - | - | maxSizeOfAnalyzeProfileSnapshot|The max number of snapshots analyzed by OAP| - | 12000 |
|cluster|standalone| - | standalone is not suitable for one node running, no available configuration.| - | - |
| - | zookeeper|nameSpace|The namespace, represented by root path, isolates the configurations in the zookeeper.|SW_NAMESPACE| `/`, root path|
| - | - | hostPort|hosts and ports of Zookeeper Cluster|SW_CLUSTER_ZK_HOST_PORT| localhost:2181|
| - | - | baseSleepTimeMs|The period of Zookeeper client between two retries. Unit is ms.|SW_CLUSTER_ZK_SLEEP_TIME|1000|
| - | - | maxRetries| The max retry time of re-trying.|SW_CLUSTER_ZK_MAX_RETRIES|3|
| - | - | enableACL| Open ACL by using `schema` and `expression`|SW_ZK_ENABLE_ACL| false|
| - | - | schema | schema for the authorization|SW_ZK_SCHEMA|digest|
| - | - | expression | expression for the authorization|SW_ZK_EXPRESSION|skywalking:skywalking|
| - | - | internalComHost| The hostname registered in the Zookeeper for the internal communication of OAP cluster.| - | -|
| - | - | internalComPort| The port registered in the Zookeeper for the internal communication of OAP cluster.| - | -1|
| - | kubernetes| namespace| Namespace SkyWalking deployed in the k8s|SW_CLUSTER_K8S_NAMESPACE|default|
| - | - | labelSelector| Labels used for filtering the OAP deployment in the k8s|SW_CLUSTER_K8S_LABEL| app=collector,release=skywalking|
| - | - | uidEnvName| Environment variable name for reading uid. | SW_CLUSTER_K8S_UID|SKYWALKING_COLLECTOR_UID|
| - | consul| serviceName| Service name used for SkyWalking cluster. |SW_SERVICE_NAME|SkyWalking_OAP_Cluster|
| - | - | hostPort| hosts and ports used of Consul cluster.| SW_CLUSTER_CONSUL_HOST_PORT|localhost:8500|
| - | - | aclToken| ALC Token of Consul. Empty string means `without ALC token`.| SW_CLUSTER_CONSUL_ACLTOKEN | - |
| - | - | internalComHost| The hostname registered in the Consul for the internal communication of OAP cluster.| - | -|
| - | - | internalComPort| The port registered in the Consul for the internal communication of OAP cluster.| - | -1|
| - | etcd| serviceName| Service name used for SkyWalking cluster. |SW_SERVICE_NAME|SkyWalking_OAP_Cluster|
| - | - | hostPort| hosts and ports used of etcd cluster.| SW_CLUSTER_ETCD_HOST_PORT|localhost:2379|
| - | - | isSSL| Open SSL for the connection between SkyWalking and etcd cluster.| - | - |
| - | - | internalComHost| The hostname registered in the etcd for the internal communication of OAP cluster.| - | -|
| - | - | internalComPort| The port registered in the etcd for the internal communication of OAP cluster.| - | -1|
| - | Nacos| serviceName| Service name used for SkyWalking cluster. |SW_SERVICE_NAME|SkyWalking_OAP_Cluster|
| - | - | hostPort| hosts and ports used of Nacos cluster.| SW_CLUSTER_NACOS_HOST_PORT|localhost:8848|
| - | - | namespace| Namespace used by SkyWalking node coordination.| SW_CLUSTER_NACOS_NAMESPACE|public|
| - | - | internalComHost| The hostname registered in the Nacos for the internal communication of OAP cluster.| - | -|
| - | - | internalComPort| The port registered in the Nacos for the internal communication of OAP cluster.| - | -1|
| - | - | username | Nacos Auth username | SW_CLUSTER_NACOS_USERNAME | - |
| - | - | password | Nacos Auth password | SW_CLUSTER_NACOS_PASSWORD | - |
| - | - | accessKey | Nacos Auth accessKey | SW_CLUSTER_NACOS_ACCESSKEY | - |
| - | - | secretKey | Nacos Auth secretKey  | SW_CLUSTER_NACOS_SECRETKEY | - |
| storage|elasticsearch| - | ElasticSearch 6 storage implementation | - | - |
| - | - | nameSpace | Prefix of indexes created and used by SkyWalking. | SW_NAMESPACE | - |
| - | - | clusterNodes | ElasticSearch cluster nodes for client connection.| SW_STORAGE_ES_CLUSTER_NODES |localhost|
| - | - | protocol | HTTP or HTTPs. | SW_STORAGE_ES_HTTP_PROTOCOL | HTTP|
| - | - | user| User name of ElasticSearch cluster| SW_ES_USER | - |
| - | - | password | Password of ElasticSearch cluster | SW_ES_PASSWORD | - |
| - | - | trustStorePath | Trust JKS file path. Only work when user name and password opened | SW_STORAGE_ES_SSL_JKS_PATH | - |
| - | - | trustStorePass | Trust JKS file password. Only work when user name and password opened | SW_STORAGE_ES_SSL_JKS_PASS | - |
| - | - | secretsManagementFile| Secrets management file in the properties format includes the username, password, which are managed by 3rd party tool. Provide the capability to update them in the runtime.|SW_ES_SECRETS_MANAGEMENT_FILE | - |
| - | - | dayStep| Represent the number of days in the one minute/hour/day index.| SW_STORAGE_DAY_STEP | 1|
| - | - | indexShardsNumber | Shard number of new indexes | SW_STORAGE_ES_INDEX_SHARDS_NUMBER | 1 |
| - | - | indexReplicasNumber | Replicas number of new indexes | SW_STORAGE_ES_INDEX_REPLICAS_NUMBER | 0 |
| - | - | superDatasetDayStep | Represent the number of days in the super size dataset record index, the default value is the same as dayStep when the value is less than 0.|SW_SUPERDATASET_STORAGE_DAY_STEP|-1 |
| - | - | superDatasetIndexShardsFactor | Super data set has been defined in the codes, such as trace segments. This factor provides more shards for the super data set, shards number = indexShardsNumber * superDatasetIndexShardsFactor. Also, this factor effects Zipkin and Jaeger traces.|SW_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR|5 |
| - | - | superDatasetIndexReplicasNumber | Represent the replicas number in the super size dataset record index.|SW_STORAGE_ES_SUPER_DATASET_INDEX_REPLICAS_NUMBER|0 |
| - | - | bulkActions| Async bulk size of the record data batch execution. | SW_STORAGE_ES_BULK_ACTIONS| 1000|
| - | - | syncBulkActions| Sync bulk size of the metrics data batch execution. | SW_STORAGE_ES_SYNC_BULK_ACTIONS| 50000|
| - | - | flushInterval| Period of flush, no matter `bulkActions` reached or not. Unit is second.| SW_STORAGE_ES_FLUSH_INTERVAL | 10|
| - | - | concurrentRequests| The number of concurrent requests allowed to be executed. | SW_STORAGE_ES_CONCURRENT_REQUESTS| 2 |
| - | - | resultWindowMaxSize | The max size of dataset when OAP loading cache, such as network alias. | SW_STORAGE_ES_QUERY_MAX_WINDOW_SIZE | 10000|
| - | - | metadataQueryMaxSize | The max size of metadata per query. | SW_STORAGE_ES_QUERY_MAX_SIZE | 5000 |
| - | - | segmentQueryMaxSize | The max size of trace segments per query. | SW_STORAGE_ES_QUERY_SEGMENT_SIZE | 200|
| - | - | profileTaskQueryMaxSize | The max size of profile task per query. | SW_STORAGE_ES_QUERY_PROFILE_TASK_SIZE | 200|
| - | - | advanced | All settings of ElasticSearch index creation. The value should be in JSON format | SW_STORAGE_ES_ADVANCED | - |
| - |elasticsearch7| - | ElasticSearch 7 storage implementation | - | - |
| - | - | nameSpace | Prefix of indexes created and used by SkyWalking. | SW_NAMESPACE | - |
| - | - | clusterNodes | ElasticSearch cluster nodes for client connection.| SW_STORAGE_ES_CLUSTER_NODES |localhost|
| - | - | protocol | HTTP or HTTPs. | SW_STORAGE_ES_HTTP_PROTOCOL | HTTP|
| - | - | user| User name of ElasticSearch cluster| SW_ES_USER | - |
| - | - | password | Password of ElasticSearch cluster | SW_ES_PASSWORD | - |
| - | - | trustStorePath | Trust JKS file path. Only work when user name and password opened | SW_STORAGE_ES_SSL_JKS_PATH | - |
| - | - | trustStorePass | Trust JKS file password. Only work when user name and password opened | SW_STORAGE_ES_SSL_JKS_PASS | - |
| - | - | secretsManagementFile| Secrets management file in the properties format includes the username, password, which are managed by 3rd party tool. Provide the capability to update them in the runtime.|SW_ES_SECRETS_MANAGEMENT_FILE | - |
| - | - | dayStep| Represent the number of days in the one minute/hour/day index.| SW_STORAGE_DAY_STEP | 1|
| - | - | indexShardsNumber | Shard number of new indexes | SW_STORAGE_ES_INDEX_SHARDS_NUMBER | 1 |
| - | - | indexReplicasNumber | Replicas number of new indexes | SW_STORAGE_ES_INDEX_REPLICAS_NUMBER | 0 |
| - | - | superDatasetDayStep | Represent the number of days in the super size dataset record index, the default value is the same as dayStep when the value is less than 0.|SW_SUPERDATASET_STORAGE_DAY_STEP|-1 |
| - | - | superDatasetIndexShardsFactor | Super data set has been defined in the codes, such as trace segments. This factor provides more shards for the super data set, shards number = indexShardsNumber * superDatasetIndexShardsFactor. Also, this factor effects Zipkin and Jaeger traces.|SW_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR|5 |
| - | - | superDatasetIndexReplicasNumber | Represent the replicas number in the super size dataset record index.|SW_STORAGE_ES_SUPER_DATASET_INDEX_REPLICAS_NUMBER|0 |
| - | - | bulkActions| Async bulk size of the record data batch execution. | SW_STORAGE_ES_BULK_ACTIONS| 1000|
| - | - | syncBulkActions| Sync bulk size of the metrics data batch execution. | SW_STORAGE_ES_SYNC_BULK_ACTIONS| 50000|
| - | - | flushInterval| Period of flush, no matter `bulkActions` reached or not. Unit is second.| SW_STORAGE_ES_FLUSH_INTERVAL | 10|
| - | - | concurrentRequests| The number of concurrent requests allowed to be executed. | SW_STORAGE_ES_CONCURRENT_REQUESTS| 2 |
| - | - | resultWindowMaxSize | The max size of dataset when OAP loading cache, such as network alias. | SW_STORAGE_ES_QUERY_MAX_WINDOW_SIZE | 10000|
| - | - | metadataQueryMaxSize | The max size of metadata per query. | SW_STORAGE_ES_QUERY_MAX_SIZE | 5000 |
| - | - | segmentQueryMaxSize | The max size of trace segments per query. | SW_STORAGE_ES_QUERY_SEGMENT_SIZE | 200|
| - | - | profileTaskQueryMaxSize | The max size of profile task per query. | SW_STORAGE_ES_QUERY_PROFILE_TASK_SIZE | 200|
| - | - | advanced | All settings of ElasticSearch index creation. The value should be in JSON format | SW_STORAGE_ES_ADVANCED | - |
| - |h2| - |  H2 storage is designed for demonstration and running in short term(1-2 hours) only | - | - |
| - | - | driver | H2 JDBC driver. | SW_STORAGE_H2_DRIVER | org.h2.jdbcx.JdbcDataSource|
| - | - | url | H2 connection URL. Default is H2 memory mode | SW_STORAGE_H2_URL | jdbc:h2:mem:skywalking-oap-db |
| - | - | user | User name of H2 database. | SW_STORAGE_H2_USER | sa |
| - | - | password | Password of H2 database. | - | - | 
| - | - | metadataQueryMaxSize | The max size of metadata per query. | SW_STORAGE_H2_QUERY_MAX_SIZE | 5000 |
| - | - | maxSizeOfArrayColumn | Some entities, such as trace segment, include the logic column with multiple values. In the H2, we use multiple physical columns to host the values, such as, Change column_a with values [1,2,3,4,5] to `column_a_0 = 1, column_a_1 = 2, column_a_2 = 3 , column_a_3 = 4, column_a_4 = 5` | SW_STORAGE_MAX_SIZE_OF_ARRAY_COLUMN | 20 |
| - | - | numOfSearchableValuesPerTag | In a trace segment, it includes multiple spans with multiple tags. Different spans could have same tag keys, such as multiple HTTP exit spans all have their own `http.method` tag. This configuration set the limitation of max num of values for the same tag key. | SW_STORAGE_NUM_OF_SEARCHABLE_VALUES_PER_TAG | 2 |
| - |mysql| - | MySQL Storage. The MySQL JDBC Driver is not in the dist, please copy it into oap-lib folder manually | - | - |
| - | - | properties | Hikari connection pool configurations | - | Listed in the `application.yaml`. |
| - | - | metadataQueryMaxSize | The max size of metadata per query. | SW_STORAGE_MYSQL_QUERY_MAX_SIZE | 5000 |
| - | - | maxSizeOfArrayColumn | Some entities, such as trace segment, include the logic column with multiple values. In the MySQL, we use multiple physical columns to host the values, such as, Change column_a with values [1,2,3,4,5] to `column_a_0 = 1, column_a_1 = 2, column_a_2 = 3 , column_a_3 = 4, column_a_4 = 5` | SW_STORAGE_MAX_SIZE_OF_ARRAY_COLUMN | 20 |
| - | - | numOfSearchableValuesPerTag | In a trace segment, it includes multiple spans with multiple tags. Different spans could have same tag keys, such as multiple HTTP exit spans all have their own `http.method` tag. This configuration set the limitation of max num of values for the same tag key. | SW_STORAGE_NUM_OF_SEARCHABLE_VALUES_PER_TAG | 2 |
| - |influxdb| - | InfluxDB storage. |- | - |
| - | - | url| InfluxDB connection URL. | SW_STORAGE_INFLUXDB_URL | http://localhost:8086|
| - | - | user | User name of InfluxDB. | SW_STORAGE_INFLUXDB_USER | root|
| - | - | password | Password of InfluxDB. | SW_STORAGE_INFLUXDB_PASSWORD | -|
| - | - | database | Database of InfluxDB. | SW_STORAGE_INFLUXDB_DATABASE | skywalking |
| - | - | actions | The number of actions to collect. | SW_STORAGE_INFLUXDB_ACTIONS | 1000 |
| - | - | duration | The time to wait at most (milliseconds). | SW_STORAGE_INFLUXDB_DURATION | 1000|
| - | - | batchEnabled | If true, write points with batch api. | SW_STORAGE_INFLUXDB_BATCH_ENABLED | true|
| - | - | fetchTaskLogMaxSize | The max number of fetch task log in a request. | SW_STORAGE_INFLUXDB_FETCH_TASK_LOG_MAX_SIZE | 5000|
| - | - | connectionResponseFormat | The response format of connection to influxDB, cannot be anything but MSGPACK or JSON. | SW_STORAGE_INFLUXDB_CONNECTION_RESPONSE_FORMAT | MSGPACK |
| agent-analyzer | default | Agent Analyzer. | SW_AGENT_ANALYZER | default |
| - | -| sampleRate|Sampling rate for receiving trace. The precision is 1/10000. 10000 means 100% sample in default.|SW_TRACE_SAMPLE_RATE|10000|
| - | - |slowDBAccessThreshold|The slow database access thresholds. Unit ms.|SW_SLOW_DB_THRESHOLD|default:200,mongodb:100|
| - | - |forceSampleErrorSegment|When sampling mechanism activated, this config would make the error status segment sampled, ignoring the sampling rate.|SW_FORCE_SAMPLE_ERROR_SEGMENT|true|
| - | - |segmentStatusAnalysisStrategy|Determine the final segment status from the status of spans. Available values are `FROM_SPAN_STATUS` , `FROM_ENTRY_SPAN` and `FROM_FIRST_SPAN`. `FROM_SPAN_STATUS` represents the segment status would be error if any span is in error status. `FROM_ENTRY_SPAN` means the segment status would be determined by the status of entry spans only. `FROM_FIRST_SPAN` means the segment status would be determined by the status of the first span only.|SW_SEGMENT_STATUS_ANALYSIS_STRATEGY|FROM_SPAN_STATUS|
| - | - |noUpstreamRealAddressAgents|Exit spans with the component in the list would not generate the client-side instance relation metrics. As some tracing plugins can't collect the real peer ip address, such as Nginx-LUA and Envoy. |SW_NO_UPSTREAM_REAL_ADDRESS|6000,9000|
| - | - |slowTraceSegmentThreshold|Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism activated. The default value is `-1`, which means would not sample slow traces. Unit, millisecond. |SW_SLOW_TRACE_SEGMENT_THRESHOLD|-1|
| - | - |meterAnalyzerActiveFiles|Which files could be meter analyzed, files split by ","|SW_METER_ANALYZER_ACTIVE_FILES||
| receiver-sharing-server|default| Sharing server provides new gRPC and restful servers for data collection. Ana make the servers in the core module working for internal communication only.| - | - |
| - | - | restHost| Binding IP of restful service. Services include GraphQL query and HTTP data report| SW_RECEIVER_SHARING_REST_HOST | - |
| - | - | restPort | Binding port of restful service | SW_RECEIVER_SHARING_REST_PORT | - |
| - | - | restContextPath| Web context path of restful service| SW_RECEIVER_SHARING_REST_CONTEXT_PATH | - |
| - | - | restMinThreads| Min threads number of restful service| SW_RECEIVER_SHARING_JETTY_MIN_THREADS|1|
| - | - | restMaxThreads| Max threads number of restful service| SW_RECEIVER_SHARING_JETTY_MAX_THREADS|200|
| - | - | restIdleTimeOut| Connector idle timeout in milliseconds of restful service| SW_RECEIVER_SHARING_JETTY_IDLE_TIMEOUT|30000|
| - | - | restAcceptorPriorityDelta| Thread priority delta to give to acceptor threads of restful service| SW_RECEIVER_SHARING_JETTY_DELTA|0|
| - | - | restAcceptQueueSize| ServerSocketChannel backlog  of restful service| SW_RECEIVER_SHARING_JETTY_QUEUE_SIZE|0|
| - | - | gRPCHost|Binding IP of gRPC service. Services include gRPC data report and internal communication among OAP nodes| SW_RECEIVER_GRPC_HOST | 0.0.0.0. Not Activated |
| - | - | gRPCPort| Binding port of gRPC service | SW_RECEIVER_GRPC_PORT | Not Activated |
| - | - | gRPCThreadPoolSize|Pool size of gRPC server| SW_RECEIVER_GRPC_THREAD_POOL_SIZE | CPU core * 4|
| - | - | gRPCThreadPoolQueueSize| The queue size of gRPC server| SW_RECEIVER_GRPC_POOL_QUEUE_SIZE | 10000|
| - | - | gRPCSslEnabled| Activate SSL for gRPC service | SW_RECEIVER_GRPC_SSL_ENABLED | false |
| - | - | gRPCSslKeyPath| The file path of gRPC SSL key| SW_RECEIVER_GRPC_SSL_KEY_PATH | - |
| - | - | gRPCSslCertChainPath| The file path of gRPC SSL cert chain| SW_RECEIVER_GRPC_SSL_CERT_CHAIN_PATH | - |
| - | - | maxConcurrentCallsPerConnection | The maximum number of concurrent calls permitted for each incoming connection. Defaults to no limit. | SW_RECEIVER_GRPC_MAX_CONCURRENT_CALL | - |
| - | - | authentication | The token text for the authentication. Work for gRPC connection only. Once this is set, the client is required to use the same token. | SW_AUTHENTICATION | - |
| receiver-register|default| Read [receiver doc](backend-receivers.md) for more details | - | - |
| receiver-trace|default| Read [receiver doc](backend-receivers.md) for more details | - | - |
| receiver-jvm| default| Read [receiver doc](backend-receivers.md) for more details | - | - |
| receiver-clr| default| Read [receiver doc](backend-receivers.md) for more details | - | - |
| receiver-profile| default| Read [receiver doc](backend-receivers.md) for more details | - | - |
| service-mesh| default| Read [receiver doc](backend-receivers.md) for more details | - | - |
| envoy-metric| default| Read [receiver doc](backend-receivers.md) for more details | - | - |
| - | - | acceptMetricsService | Open Envoy Metrics Service analysis | SW_ENVOY_METRIC_SERVICE | true|
| - | - | alsHTTPAnalysis | Open Envoy Access Log Service analysis. Value = `k8s-mesh` means open the analysis | SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS | - |
| - | - | k8sServiceNameRule | `k8sServiceNameRule` allows you to customize the service name in ALS via Kubernetes metadata, the available variables are `pod`, `service`, e.g., you can use `${service.metadata.name}-${pod.metadata.labels.version}` to append the version number to the service name. Be careful, when using environment variables to pass this configuration, use single quotes(`''`) to avoid it being evaluated by the shell. | - |
| receiver-otel | default | Read [receiver doc](backend-receivers.md) for more details | - | - |
| - | - | enabledHandlers|Enabled handlers for otel| SW_OTEL_RECEIVER_ENABLED_HANDLERS | - |
| - | - | enabledOcRules|Enabled metric rules for OC handler | SW_OTEL_RECEIVER_ENABLED_OC_RULES | - |
| receiver_zipkin |default| Read [receiver doc](backend-receivers.md) | - | - |
| - | - | restHost| Binding IP of restful service. |SW_RECEIVER_ZIPKIN_HOST|0.0.0.0|
| - | - | restPort | Binding port of restful service | SW_RECEIVER_ZIPKIN_PORT|9411|
| - | - | restContextPath| Web context path of restful service| SW_RECEIVER_ZIPKIN_CONTEXT_PATH|/|
| - | - | needAnalysis|Analysis zipkin span to generate metrics| - | false|
| - | - | maxCacheSize| Max cache size for span analysis | - | 1_000_000 |
| - | - | expireTime| The expire time of analysis cache, unit is second. | - | 20|
| receiver_jaeger | default| Read [receiver doc](backend-receivers.md) | - | - |
| - | - | gRPCHost|Binding IP of gRPC service. Services include gRPC data report and internal communication among OAP nodes| SW_RECEIVER_JAEGER_HOST | - |
| - | - | gRPCPort| Binding port of gRPC service | SW_RECEIVER_JAEGER_PORT | - |
| - | - | gRPCThreadPoolSize|Pool size of gRPC server| - | CPU core * 4|
| - | - | gRPCThreadPoolQueueSize| The queue size of gRPC server| - | 10000|
| - | - | maxConcurrentCallsPerConnection | The maximum number of concurrent calls permitted for each incoming connection. Defaults to no limit. | - | - |
| - | - | maxMessageSize | Sets the maximum message size allowed to be received on the server. Empty means 4 MiB | - | 4M(based on Netty) |
| prometheus-fetcher | default | Read [fetcher doc](backend-fetcher.md) for more details | - | - |
| - | - | active | Activate the Prometheus fetcher. | SW_PROMETHEUS_FETCHER_ACTIVE | false |
| kafka-fetcher | default | Read [fetcher doc](backend-fetcher.md) for more details | - | - |
| - | - | bootstrapServers | A list of host/port pairs to use for establishing the initial connection to the Kafka cluster. | SW_KAFKA_FETCHER_SERVERS | localhost:9092 |
| - | - | groupId | A unique string that identifies the consumer group this consumer belongs to.| - | skywalking-consumer |
| - | - | consumePartitions | Which PartitionId(s) of the topics assign to the OAP server. If more than one, is separated by commas. | SW_KAFKA_FETCHER_CONSUME_PARTITIONS | - |
| - | - | isSharding | it was true when OAP Server in cluster. | SW_KAFKA_FETCHER_IS_SHARDING | false |
| - | - | createTopicIfNotExist | If true, create the Kafka topic when it does not exist. | - | true |
| - | - | partitions | The number of partitions for the topic being created. | SW_KAFKA_FETCHER_PARTITIONS | 3 |
| - | - | enableMeterSystem | To enable to fetch and handle [Meter System](backend-meter.md) data. | SW_KAFKA_FETCHER_ENABLE_METER_SYSTEM | false |
| - | - | replicationFactor | The replication factor for each partition in the topic being created. | SW_KAFKA_FETCHER_PARTITIONS_FACTOR | 2 |
| - | - | kafkaHandlerThreadPoolSize | Pool size of kafka message handler executor. | SW_KAFKA_HANDLER_THREAD_POOL_SIZE | CPU core * 2 |
| - | - | kafkaHandlerThreadPoolQueueSize | The queue size of kafka message handler executor. | SW_KAFKA_HANDLER_THREAD_POOL_QUEUE_SIZE | 10000 |
| - | - | topicNameOfMeters | Specifying Kafka topic name for Meter system data. | - | skywalking-meters |
| - | - | topicNameOfMetrics | Specifying Kafka topic name for JVM Metrics data. | - | skywalking-metrics |
| - | - | topicNameOfProfiling | Specifying Kafka topic name for Profiling data. | - | skywalking-profilings |
| - | - | topicNameOfTracingSegments | Specifying Kafka topic name for Tracing data. | - | skywalking-segments |
| - | - | topicNameOfManagements | Specifying Kafka topic name for service instance reporting and registering. | - | skywalking-managements |
| receiver-browser | default | Read [receiver doc](backend-receivers.md) for more details | - | - | - |
| - | - | sampleRate | Sampling rate for receiving trace. The precision is 1/10000. 10000 means 100% sample in default. | SW_RECEIVER_BROWSER_SAMPLE_RATE | 10000 |
| query | graphql | - | GraphQL query implementation | - |
| - | - | path | Root path of GraphQL query and mutation. | SW_QUERY_GRAPHQL_PATH | /graphql|
| alarm | default | - | Read [alarm doc](backend-alarm.md) for more details. | - |
| telemetry | - | - | Read [telemetry doc](backend-telemetry.md) for more details. | - | 
| - | none| - | No op implementation | - |
| - | prometheus| host | Binding host for Prometheus server fetching data| SW_TELEMETRY_PROMETHEUS_HOST|0.0.0.0|
| - | - | port|  Binding port for Prometheus server fetching data|SW_TELEMETRY_PROMETHEUS_PORT|1234|
| configuration | - | - | Read [dynamic configuration doc](dynamic-config.md) for more details. | - |
| - | grpc| host | DCS server binding hostname | SW_DCS_SERVER_HOST | - |
| - | - | port | DCS server binding port | SW_DCS_SERVER_PORT | 80 |
| - | - | clusterName | Cluster name when reading latest configuration from DSC server. | SW_DCS_CLUSTER_NAME | SkyWalking|
| - | - | period | The period of OAP reading data from DSC server. Unit is second. | SW_DCS_PERIOD | 20 |
| - | apollo| apolloMeta| `apollo.meta` in Apollo | SW_CONFIG_APOLLO | http://106.12.25.204:8080 | 
| - | - | apolloCluster | `apollo.cluster` in Apollo | SW_CONFIG_APOLLO_CLUSTER | default|
| - | - | apolloEnv | `env` in Apollo | SW_CONFIG_APOLLO_ENV | - |
| - | - | appId | `app.id` in Apollo | SW_CONFIG_APOLLO_APP_ID | skywalking |
| - | - | period | The period of data sync. Unit is second. | SW_CONFIG_APOLLO_PERIOD | 60 |
| - | zookeeper|nameSpace|The namespace, represented by root path, isolates the configurations in the zookeeper.|SW_CONFIG_ZK_NAMESPACE| `/`, root path|
| - | - | hostPort|hosts and ports of Zookeeper Cluster|SW_CONFIG_ZK_HOST_PORT| localhost:2181|
| - | - | baseSleepTimeMs|The period of Zookeeper client between two retries. Unit is ms.|SW_CONFIG_ZK_BASE_SLEEP_TIME_MS|1000|
| - | - | maxRetries| The max retry time of re-trying.|SW_CONFIG_ZK_MAX_RETRIES|3|
| - | - | period | The period of data sync. Unit is second. | SW_CONFIG_ZK_PERIOD | 60 |
| - | etcd| clusterName| Service name used for SkyWalking cluster. |SW_CONFIG_ETCD_CLUSTER_NAME|default|
| - | - | serverAddr| hosts and ports used of etcd cluster.| SW_CONFIG_ETCD_SERVER_ADDR|localhost:2379|
| - | - | group |Additional prefix of the configuration key| SW_CONFIG_ETCD_GROUP | skywalking|
| - | - | period | The period of data sync. Unit is second. | SW_CONFIG_ZK_PERIOD | 60 
| - | consul | hostPort| hosts and ports used of Consul cluster.| SW_CONFIG_CONSUL_HOST_AND_PORTS|localhost:8500|
| - | - | aclToken| ALC Token of Consul. Empty string means `without ALC token`.| SW_CONFIG_CONSUL_ACL_TOKEN | - |
| - | - | period | The period of data sync. Unit is second. | SW_CONFIG_CONSUL_PERIOD | 60 |
| - | k8s-configmap | namespace | Deployment namespace of the config map. |SW_CLUSTER_K8S_NAMESPACE|default|
| - | - | labelSelector| Labels used for locating configmap. |SW_CLUSTER_K8S_LABEL|app=collector,release=skywalking|
| - | - | period | The period of data sync. Unit is second. | SW_CONFIG_ZK_PERIOD | 60 |
| - | nacos | serverAddr | Nacos Server Host | SW_CONFIG_NACOS_SERVER_ADDR | 127.0.0.1|
| - | - | port | Nacos Server Port | SW_CONFIG_NACOS_SERVER_PORT | 8848 |
| - | - | group | Nacos Configuration namespace | SW_CONFIG_NACOS_SERVER_NAMESPACE | - |
| - | - | period | The period of data sync. Unit is second. | SW_CONFIG_CONFIG_NACOS_PERIOD | 60 |
| - | - | username | Nacos Auth username | SW_CONFIG_NACOS_USERNAME | - |
| - | - | password | Nacos Auth password | SW_CONFIG_NACOS_PASSWORD | - |
| - | - | accessKey | Nacos Auth accessKey | SW_CONFIG_NACOS_ACCESSKEY | - |
| - | - | secretKey | Nacos Auth secretKey  | SW_CONFIG_NACOS_SECRETKEY | - |
| exporter | grpc | targetHost | The host of target grpc server for receiving export data. | SW_EXPORTER_GRPC_HOST | 127.0.0.1 |
| - | - | targetPort | The port of target grpc server for receiving export data. | SW_EXPORTER_GRPC_PORT | 9870 |
| health-checker | default | checkIntervalSeconds | The period of check OAP internal health status. Unit is second. | SW_HEALTH_CHECKER_INTERVAL_SECONDS | 5 |

## Notice
¹ System Environment Variable name could be declared and changed in the application.yml. The names listed here,
are just provided in the default `application.yml` file.
