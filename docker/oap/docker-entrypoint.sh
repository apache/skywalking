# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/bin/bash

set -e

var_application_file="config/application.yml"

generateClusterStandalone() {
    echo "cluster:" >> ${var_application_file}
    echo "  standalone:" >> ${var_application_file}
}

generateClusterZookeeper() {
    cat <<EOT >> ${var_application_file}
cluster:
  zookeeper:
    nameSpace: \${SW_NAMESPACE:""}
    hostPort: \${SW_CLUSTER_ZK_HOST_PORT:zookeeper:2181}
    #Retry Policy
    baseSleepTimeMs: \${SW_CLUSTER_ZK_SLEEP_TIME:1000} # initial amount of time to wait between retries
    maxRetries: \${SW_CLUSTER_ZK_MAX_RETRIES:3} # max number of times to retry
    # Enable ACL
    enableACL: \${SW_ZK_ENABLE_ACL:false} # disable ACL in default
    schema: \${SW_ZK_SCHEMA:digest} # only support digest schema
    expression: \${SW_ZK_EXPRESSION:skywalking:skywalking}
EOT
}

generateClusterK8s() {
    cat <<EOT >> ${var_application_file}
cluster:
  kubernetes:
    watchTimeoutSeconds: \${SW_CLUSTER_K8S_WATCH_TIMEOUT:60}
    namespace: \${SW_CLUSTER_K8S_NAMESPACE:default}
    labelSelector: \${SW_CLUSTER_K8S_LABEL:app=collector,release=skywalking}
    uidEnvName: \${SW_CLUSTER_K8S_UID:SKYWALKING_COLLECTOR_UID}
EOT
}

generateClusterConsul() {
     cat <<EOT >> ${var_application_file}
cluster:
  consul:
    serviceName: \${SW_SERVICE_NAME:"SkyWalking_OAP_Cluster"}
    # Consul cluster nodes, example: 10.0.0.1:8500,10.0.0.2:8500,10.0.0.3:8500
    hostPort: \${SW_CLUSTER_CONSUL_HOST_PORT:consul:8500}
EOT
}

generateClusterEtcd() {
    cat <<EOT >> ${var_application_file}
cluster:
  etcd:
    serviceName: \${SW_SERVICE_NAME:"SkyWalking_OAP_Cluster"}
    # Etcd cluster nodes, example: 10.0.0.1:2379,10.0.0.2:2379,10.0.0.3:2379
    hostPort: \${SW_CLUSTER_ETCD_HOST_PORT:etcd:2379}
EOT
}

generateClusterNacos() {
    cat <<EOT >> ${var_application_file}
cluster:
  nacos:
    serviceName: \${SW_SERVICE_NAME:"SkyWalking_OAP_Cluster"}
    namespace: \${SW_CLUSTER_NACOS_NAMESPACE:""}
    hostPort: \${SW_CLUSTER_NACOS_HOST_PORT:nacos:8848}
EOT
}

generateStorageElastisearch() {
if [[ "$SW_RECEIVER_ZIPKIN_ENABLED" = "true" ]]; then
    cat <<EOT >> ${var_application_file}
storage:
  zipkin-elasticsearch:
EOT
elif [[ "$SW_RECEIVER_JAEGER_ENABLED" = "true" ]]; then
    cat <<EOT >> ${var_application_file}
storage:
  jaeger-elasticsearch:
EOT
else
    cat <<EOT >> ${var_application_file}
storage:
  elasticsearch:
EOT
fi
cat <<EOT >> ${var_application_file}
    nameSpace: \${SW_NAMESPACE:""}
    clusterNodes: \${SW_STORAGE_ES_CLUSTER_NODES:localhost:9200}
    protocol: \${SW_STORAGE_ES_HTTP_PROTOCOL:"http"}
    user: \${SW_ES_USER:""}
    password: \${SW_ES_PASSWORD:""}
    indexShardsNumber: \${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:2}
    indexReplicasNumber: \${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:0}
    # Those data TTL settings will override the same settings in core module.
    recordDataTTL: \${SW_STORAGE_ES_RECORD_DATA_TTL:7} # Unit is day
    otherMetricsDataTTL: \${SW_STORAGE_ES_OTHER_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: \${SW_STORAGE_ES_MONTH_METRIC_DATA_TTL:18} # Unit is month
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: \${SW_STORAGE_ES_BULK_ACTIONS:2000} # Execute the bulk every 2000 requests
    bulkSize: \${SW_STORAGE_ES_BULK_SIZE:20} # flush the bulk every 20mb
    flushInterval: \${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: \${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
    resultWindowMaxSize: \${SW_STORAGE_ES_QUERY_MAX_WINDOW_SIZE:10000}
    metadataQueryMaxSize: \${SW_STORAGE_ES_QUERY_MAX_SIZE:5000}
    segmentQueryMaxSize: \${SW_STORAGE_ES_QUERY_SEGMENT_SIZE:200}
    profileTaskQueryMaxSize: \${SW_STORAGE_ES_QUERY_PROFILE_TASK_SIZE:200}
EOT
}

generateStorageH2() {
    cat <<EOT >> ${var_application_file}
storage:
  h2:
    driver: \${SW_STORAGE_H2_DRIVER:org.h2.jdbcx.JdbcDataSource}
    url: \${SW_STORAGE_H2_URL:jdbc:h2:mem:skywalking-oap-db}
    user: \${SW_STORAGE_H2_USER:sa}
    metadataQueryMaxSize: \${SW_STORAGE_H2_QUERY_MAX_SIZE:5000}
EOT
}

generateStorageMySQL() {
    cat <<EOT >> ${var_application_file}
storage:
  mysql:
    properties:
        jdbcUrl: \${SW_JDBC_URL:"jdbc:mysql://localhost:3306/swtest"}
        dataSource.user: \${SW_DATA_SOURCE_USER:root}
        dataSource.password: \${SW_DATA_SOURCE_PASSWORD:root@1234}
        dataSource.cachePrepStmts: \${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
        dataSource.prepStmtCacheSize: \${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
        dataSource.prepStmtCacheSqlLimit: \${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
        dataSource.useServerPrepStmts: \${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: \${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
EOT
}

generateConfigurationNone() {
    cat <<EOT >> ${var_application_file}
configuration:
  none:
EOT
}

generateConfigurationApollo() {
    cat <<EOT >> ${var_application_file}
configuration:
  apollo:
    apolloMeta: \${SW_CONFIGURATION_APOLLO_META:http://apollo:8080}
    apolloCluster: \${SW_CONFIGURATION_APOLLO_CLUSTER:default}
    apolloEnv: \${SW_CONFIGURATION_APOLLO_ENV:""}
    appId: \${SW_CONFIGURATION_APOLLO_APP_ID:skywalking}
    period: \${SW_CONFIGURATION_APOLLO_PERIOD:5}
EOT
}

generateConfigurationNacos() {
    cat <<EOT >> ${var_application_file}
configuration:
  nacos:
    # Nacos Server Host
    serverAddr: \${SW_CONFIGURATION_NACOS_SERVER_ADDR:nacos}
    # Nacos Server Port
    port: \${SW_CONFIGURATION_NACOS_PORT:8848}
    # Nacos Configuration Group
    group: \${SW_CONFIGURATION_NACOS_GROUP:skywalking}
    # Nacos Configuration namespace
    namespace: \${SW_CONFIGURATION_NACOS_NAMESPACE:""}
    # Unit seconds, sync period. Default fetch every 60 seconds.
    period : \${SW_CONFIGURATION_NACOS_PERIOD:5}
    # the name of current cluster, set the name if you want to upstream system known.
    clusterName: \${SW_CONFIGURATION_NACOS_CLUSTER_NAME:default}
EOT
}

generateConfigurationZookeeper() {
    cat <<EOT >> ${var_application_file}
configuration:
  zookeeper:
    period: \${SW_CONFIGURATION_ZOOKEEPER_PERIOD:60} # Unit seconds, sync period. Default fetch every 60 seconds.
    nameSpace: \${SW_CONFIGURATION_ZOOKEEPER_NAMESPACE:/default}
    hostPort: \${SW_CONFIGURATION_ZOOKEEPER_HOST_PATH:localhost:2181}
    #Retry Policy
    baseSleepTimeMs: \${SW_CONFIGURATION_ZOOKEEPER_BASE_SLEEP_TIME_MS:1000} # initial amount of time to wait between retries
    maxRetries: \${SW_CONFIGURATION_ZOOKEEPER_MAX_RETRIES:3}3 # max number of times to retry
EOT
}

generateConfigurationGRPC() {
    cat <<EOT >> ${var_application_file}
configuration:
  grpc:
    host: \${SW_CONFIGURATION_GRPC_HOST:127.0.0.1}
    port: \${SW_CONFIGURATION_GRPC_PORT:9555}
    period: \${SW_CONFIGURATION_GRPC_PERIOD:60}
    clusterName: \${SW_CONFIGURATION_GRPC_CLUSTER_NAME:"default"}
EOT
}

generateConfigurationConsul() {
    cat <<EOT >> ${var_application_file}
configuration:
  consul:
    # Consul host and ports, separated by comma, e.g. 1.2.3.4:8500,2.3.4.5:8500
    hostAndPorts: \${SW_CONFIGURATION_CONSUL_ADDRESS:127.0.0.1:8500}
    # Sync period in seconds. Defaults to 60 seconds.
    period: \${SW_CONFIGURATION_CONSUL_PERIOD:60}
EOT
}

generateTelemetryNone() {
    cat <<EOT >> ${var_application_file}
telemetry:
  none:
EOT
}

generateTelemetryPrometheus() {
    cat <<EOT >> ${var_application_file}
telemetry:
  prometheus:
    host: \${SW_TELEMETRY_PROMETHEUS_HOST:0.0.0.0}
    port: \${SW_TELEMETRY_PROMETHEUS_PORT:1234}
EOT
}

generateTelemetrySo11y() {
    cat <<EOT >> ${var_application_file}
telemetry:
  so11y:
    prometheusExporterEnabled: \${SW_TELEMETRY_SO11Y_PROMETHEUS_ENABLED:true}
    prometheusExporterHost: \${SW_TELEMETRY_PROMETHEUS_HOST:0.0.0.0}
    prometheusExporterPort: \${SW_TELEMETRY_PROMETHEUS_PORT:1234}
EOT
}

validateVariables() {
    name=$1; value=$2; list=$3
    valid=false
    for c in ${list} ; do
        if [[ "$c" = "$value" ]]; then
            valid=true
        fi
    done

    if ! ${valid}; then
        echo "Error: $name=$value please specify $name = $list"
        exit 1
    fi
}

generateApplicationYaml() {
    # validate
    [[ -z "$SW_CLUSTER" ]] && [[ -z "$SW_STORAGE" ]] && [[ -z "$SW_CONFIGURATION" ]] \
        && [[ -z "$SW_TELEMETRY" ]] \
        && { echo "Error: please specify \"SW_CLUSTER\" \"SW_STORAGE\" \"SW_CONFIGURATION\" \"SW_TELEMETRY\""; exit 1; }

    validateVariables "SW_CLUSTER" "$SW_CLUSTER" "standalone zookeeper kubernetes consul etcd nacos"

    validateVariables "SW_STORAGE" "$SW_STORAGE" "elasticsearch h2 mysql"

    validateVariables "SW_CONFIGURATION" "$SW_CONFIGURATION" "none apollo nacos zookeeper"

    validateVariables "SW_TELEMETRY" "$SW_TELEMETRY" "none prometheus so11y"

    echo "# Generated by 'docker-entrypoint.sh'" > ${var_application_file}
    #generate cluster
    case ${SW_CLUSTER} in
    standalone) generateClusterStandalone;;
    zookeeper) generateClusterZookeeper;;
    kubernetes) generateClusterK8s;;
    consul) generateClusterConsul;;
    etcd) generateClusterEtcd;;
    nacos) generateClusterNacos;;
    esac

    #generate core
    cat <<EOT >> ${var_application_file}
core:
  default:
    # Mixed: Receive agent data, Level 1 aggregate, Level 2 aggregate
    # Receiver: Receive agent data, Level 1 aggregate
    # Aggregator: Level 2 aggregate
    role: \${SW_CORE_ROLE:Mixed} # Mixed/Receiver/Aggregator
    restHost: \${SW_CORE_REST_HOST:0.0.0.0}
    restPort: \${SW_CORE_REST_PORT:12800}
    restContextPath: \${SW_CORE_REST_CONTEXT_PATH:/}
    gRPCHost: \${SW_CORE_GRPC_HOST:0.0.0.0}
    gRPCPort: \${SW_CORE_GRPC_PORT:11800}
    downsampling:
    - Hour
    - Day
    - Month
    # Set a timeout on metrics data. After the timeout has expired, the metrics data will automatically be deleted.
    enableDataKeeperExecutor: \${SW_CORE_ENABLE_DATA_KEEPER_EXECUTOR:true} # Turn it off then automatically metrics data delete will be close.
    dataKeeperExecutePeriod: \${SW_CORE_DATA_KEEPER_EXECUTE_PERIOD:5} # How often the data keeper executor runs periodically, unit is minute
    recordDataTTL: \${SW_CORE_RECORD_DATA_TTL:90} # Unit is minute
    minuteMetricsDataTTL: \${SW_CORE_MINUTE_METRIC_DATA_TTL:90} # Unit is minute
    hourMetricsDataTTL: \${SW_CORE_HOUR_METRIC_DATA_TTL:36} # Unit is hour
    dayMetricsDataTTL: \${SW_CORE_DAY_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: \${SW_CORE_MONTH_METRIC_DATA_TTL:18} # Unit is month
    # Cache metric data for 1 minute to reduce database queries, and if the OAP cluster changes within that minute,
    # the metrics may not be accurate within that minute.
    enableDatabaseSession: \${SW_CORE_ENABLE_DATABASE_SESSION:true}
    topNReportPeriod: \${SW_CORE_TOPN_REPORT_PERIOD:10}
EOT

    # generate storage
    case ${SW_STORAGE} in
    elasticsearch) generateStorageElastisearch;;
    h2) generateStorageH2;;
    mysql) generateStorageMySQL;;
    esac

    cat <<EOT >> ${var_application_file}
receiver-sharing-server:
  default:
   restHost: \${SW_RECEIVER_SHARING_REST_HOST:0.0.0.0}
   restPort: \${SW_RECEIVER_SHARING_REST_PORT:0}
   restContextPath: \${SW_RECEIVER_SHARING_REST_CONTEXT_PATH:/}
   gRPCHost: \${SW_RECEIVER_SHARING_GRPC_HOST:0.0.0.0}
   gRPCPort: \${SW_RECEIVER_SHARING_GRPC_PORT:0}
   maxConcurrentCallsPerConnection: \${SW_RECEIVER_SHARING_MAX_CONCURRENT_CALL:0}
   maxMessageSize: \${SW_RECEIVER_SHARING_MAX_MESSAGE_SIZE:0}
   gRPCThreadPoolSize: \${SW_RECEIVER_SHARING_GRPC_THREAD_POOL_SIZE:0}
   gRPCThreadPoolQueueSize: \${SW_RECEIVER_SHARING_GRPC_THREAD_POOL_QUEUE_SIZE:0}
   authentication: \${SW_AUTHENTICATION:""}
receiver-register:
  default:
receiver-trace:
  default:
    bufferPath: \${SW_RECEIVER_BUFFER_PATH:../trace-buffer/}  # Path to trace buffer files, suggest to use absolute path
    bufferOffsetMaxFileSize: \${SW_RECEIVER_BUFFER_OFFSET_MAX_FILE_SIZE:100} # Unit is MB
    bufferDataMaxFileSize: \${SW_RECEIVER_BUFFER_DATA_MAX_FILE_SIZE:500} # Unit is MB
    bufferFileCleanWhenRestart: \${SW_RECEIVER_BUFFER_FILE_CLEAN_WHEN_RESTART:false}
    sampleRate: \${SW_TRACE_SAMPLE_RATE:10000} # The sample rate precision is 1/10000. 10000 means 100% sample in default.
    slowDBAccessThreshold: \${SW_SLOW_DB_THRESHOLD:default:200,mongodb:100} # The slow database access thresholds. Unit ms.
receiver-jvm:
  default:
receiver-clr:
  default:
receiver-so11y:
  default:
receiver-profile:
  default:
service-mesh:
  default:
    bufferPath: \${SW_SERVICE_MESH_BUFFER_PATH:../mesh-buffer/}  # Path to trace buffer files, suggest to use absolute path
    bufferOffsetMaxFileSize: \${SW_SERVICE_MESH_OFFSET_MAX_FILE_SIZE:100} # Unit is MB
    bufferDataMaxFileSize: \${SW_SERVICE_MESH_BUFFER_DATA_MAX_FILE_SIZE:500} # Unit is MB
    bufferFileCleanWhenRestart: \${SW_SERVICE_MESH_BUFFER_FILE_CLEAN_WHEN_RESTART:false}
istio-telemetry:
  default:
query:
  graphql:
    path: \${SW_QUERY_GRAPHQL_PATH:/graphql}
alarm:
  default:
EOT
    # generate telemetry
    case ${SW_TELEMETRY} in
    none) generateTelemetryNone;;
    prometheus) generateTelemetryPrometheus;;
    so11y) generateTelemetrySo11y;;
    esac

    # generate configuration
    case ${SW_CONFIGURATION} in
    none) generateConfigurationNone;;
    apollo) generateConfigurationApollo;;
    nacos) generateConfigurationNacos;;
    zookeeper) generateConfigurationZookeeper;;
    consul) generateConfigurationConsul;;
    grpc) generateConfigurationGRPC;;
    esac

    cat <<EOT >> ${var_application_file}
envoy-metric:
  default:
EOT
    if [[ "$SW_ENVOY_ALS_ENABLED" = "true" ]]; then
        cat <<EOT >> ${var_application_file}
    alsHTTPAnalysis: \${SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS:k8s-mesh}
EOT
    fi

    if [[ "$SW_RECEIVER_ZIPKIN_ENABLED" = "true" ]]; then
        cat <<EOT >> ${var_application_file}
receiver_zipkin:
  default:
    host: \${SW_RECEIVER_ZIPKIN_HOST:0.0.0.0}
    port: \${SW_RECEIVER_ZIPKIN_PORT:9411}
    contextPath: \${SW_RECEIVER_ZIPKIN_CONTEXT_PATH:/}
EOT
    fi

    if [[ "$SW_RECEIVER_JAEGER_ENABLED" = "true" ]]; then
        cat <<EOT >> ${var_application_file}
receiver_jaeger:
  default:
    gRPCHost: \${SW_RECEIVER_JAEGER_HOST:0.0.0.0}
    gRPCPort: \${SW_RECEIVER_JAEGER_PORT:14250}
EOT
    fi

    if [[ "$SW_TELEMETRY" = "so11y" ]]; then
        cat <<EOT >> ${var_application_file}
receiver-so11y:
  default:
EOT
    fi

    if [[ "$SW_EXPORTER_ENABLED" = "true" ]]; then
        cat <<EOT >> ${var_application_file}
exporter:
  grpc:
    targetHost: \${SW_EXPORTER_GRPC_HOST:127.0.0.1}
    targetPort: \${SW_EXPORTER_GRPC_PORT:9870}
EOT
    fi
}

echo "[Entrypoint] Apache SkyWalking Docker Image"

SW_CLUSTER=${SW_CLUSTER:-standalone}
SW_STORAGE=${SW_STORAGE:-h2}
SW_CONFIGURATION=${SW_CONFIGURATION:-none}
SW_TELEMETRY=${SW_TELEMETRY:-none}
EXT_LIB_DIR=/skywalking/ext-libs
EXT_CONFIG_DIR=/skywalking/ext-config

# If user wants to override application.yml, the one generated by docker-entrypoint.sh should be ignored.
[[ -f ${EXT_CONFIG_DIR}/application.yml ]] && SW_L0AD_CONFIG_FILE_FROM_VOLUME=true

# Override configuration files
cp -vfR ${EXT_CONFIG_DIR}/ config/
if [[ -z "$SW_L0AD_CONFIG_FILE_FROM_VOLUME" ]] || [[ "$SW_L0AD_CONFIG_FILE_FROM_VOLUME" != "true" ]]; then
    generateApplicationYaml
    echo "Generated application.yml"
    echo "-------------------------"
    cat ${var_application_file}
    echo "-------------------------"
fi

CLASSPATH="config:$CLASSPATH"
for i in oap-libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done
for i in ${EXT_LIB_DIR}/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

set -ex
exec java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
     ${JAVA_OPTS} -classpath ${CLASSPATH} org.apache.skywalking.oap.server.starter.OAPServerStartUp "$@"
