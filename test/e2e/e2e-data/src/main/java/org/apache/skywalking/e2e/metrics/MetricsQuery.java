/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.e2e.metrics;

import com.google.common.collect.ImmutableMap;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.skywalking.e2e.AbstractQuery;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
@Accessors(fluent = true)
@EqualsAndHashCode(callSuper = true)
public class MetricsQuery extends AbstractQuery<MetricsQuery> {
    public static String SERVICE_SLA = "service_sla";
    public static String SERVICE_CPM = "service_cpm";
    public static String SERVICE_RESP_TIME = "service_resp_time";
    public static String SERVICE_APDEX = "service_apdex";
    public static String[] ALL_SERVICE_METRICS = {
        SERVICE_SLA,
        SERVICE_CPM,
        SERVICE_RESP_TIME,
        SERVICE_APDEX
    };
    public static String SERVICE_PERCENTILE = "service_percentile";
    public static String[] ALL_SERVICE_MULTIPLE_LINEAR_METRICS = {
        SERVICE_PERCENTILE
    };

    public static String ENDPOINT_CPM = "endpoint_cpm";
    public static String ENDPOINT_AVG = "endpoint_avg";
    public static String ENDPOINT_SLA = "endpoint_sla";
    public static String[] ALL_ENDPOINT_METRICS = {
        ENDPOINT_CPM,
        ENDPOINT_AVG,
        ENDPOINT_SLA,
        };
    public static String ENDPOINT_PERCENTILE = "endpoint_percentile";
    public static String[] ALL_ENDPOINT_MULTIPLE_LINEAR_METRICS = {
        ENDPOINT_PERCENTILE
    };

    public static String SERVICE_INSTANCE_RESP_TIME = "service_instance_resp_time";
    public static String SERVICE_INSTANCE_CPM = "service_instance_cpm";
    public static String SERVICE_INSTANCE_SLA = "service_instance_sla";
    public static String[] ALL_INSTANCE_METRICS = {
        SERVICE_INSTANCE_RESP_TIME,
        SERVICE_INSTANCE_CPM,
        SERVICE_INSTANCE_SLA
    };

    public static String INSTANCE_JVM_MEMORY_HEAP = "instance_jvm_memory_heap";
    public static String INSTANCE_JVM_MEMORY_HEAP_MAX = "instance_jvm_memory_heap_max";
    public static String INSTANCE_JVM_MEMORY_NOHEAP = "instance_jvm_memory_noheap";
    public static String INSTANCE_JVM_THREAD_LIVE_COUNT = "instance_jvm_thread_live_count";
    public static String INSTANCE_JVM_THREAD_DAEMON_COUNT = "instance_jvm_thread_daemon_count";
    public static String INSTANCE_JVM_THREAD_PEAK_COUNT = "instance_jvm_thread_peak_count";
    public static String INSTANCE_JVM_THREAD_RUNNABLE_STATE_THREAD_COUNT = "instance_jvm_thread_runnable_state_thread_count";
    public static String INSTANCE_JVM_CLASS_LOADED_CLASS_COUNT = "instance_jvm_class_loaded_class_count";
    public static String INSTANCE_JVM_CLASS_TOTAL_LOADED_CLASS_COUNT = "instance_jvm_class_total_loaded_class_count";
    public static String [] ALL_INSTANCE_JVM_METRICS = {
        INSTANCE_JVM_CLASS_TOTAL_LOADED_CLASS_COUNT,
        INSTANCE_JVM_CLASS_LOADED_CLASS_COUNT,
        INSTANCE_JVM_THREAD_RUNNABLE_STATE_THREAD_COUNT,
        INSTANCE_JVM_THREAD_LIVE_COUNT,
        INSTANCE_JVM_THREAD_DAEMON_COUNT,
        INSTANCE_JVM_THREAD_PEAK_COUNT,
        INSTANCE_JVM_MEMORY_NOHEAP,
        INSTANCE_JVM_MEMORY_HEAP_MAX,
        INSTANCE_JVM_MEMORY_HEAP,
    };

    public static String [] ALL_INSTANCE_JVM_METRICS_COMPAT = {
        INSTANCE_JVM_THREAD_LIVE_COUNT,
        INSTANCE_JVM_THREAD_DAEMON_COUNT,
        INSTANCE_JVM_THREAD_PEAK_COUNT,
        INSTANCE_JVM_MEMORY_NOHEAP,
        INSTANCE_JVM_MEMORY_HEAP_MAX,
        INSTANCE_JVM_MEMORY_HEAP,
    };

    public static String SERVICE_RELATION_CLIENT_CPM = "service_relation_client_cpm";
    public static String SERVICE_RELATION_SERVER_CPM = "service_relation_server_cpm";
    public static String SERVICE_RELATION_CLIENT_CALL_SLA = "service_relation_client_call_sla";
    public static String SERVICE_RELATION_SERVER_CALL_SLA = "service_relation_server_call_sla";
    public static String SERVICE_RELATION_CLIENT_RESP_TIME = "service_relation_client_resp_time";
    public static String SERVICE_RELATION_SERVER_RESP_TIME = "service_relation_server_resp_time";
    public static String SERVICE_RELATION_CLIENT_P99 = "service_relation_client_p99";
    public static String SERVICE_RELATION_SERVER_P99 = "service_relation_server_p99";
    public static String[] ALL_SERVICE_RELATION_CLIENT_METRICS = {
        SERVICE_RELATION_CLIENT_CPM
    };

    public static String[] ALL_SERVICE_RELATION_SERVER_METRICS = {
        SERVICE_RELATION_SERVER_CPM
    };

    public static String SERVICE_INSTANCE_RELATION_CLIENT_CPM = "service_instance_relation_client_cpm";
    public static String SERVICE_INSTANCE_RELATION_SERVER_CPM = "service_instance_relation_server_cpm";
    public static String SERVICE_INSTANCE_RELATION_CLIENT_CALL_SLA = "service_instance_relation_client_call_sla";
    public static String SERVICE_INSTANCE_RELATION_SERVER_CALL_SLA = "service_instance_relation_server_call_sla";
    public static String SERVICE_INSTANCE_RELATION_CLIENT_RESP_TIME = "service_instance_relation_client_resp_time";
    public static String SERVICE_INSTANCE_RELATION_SERVER_RESP_TIME = "service_instance_relation_server_resp_time";
    public static String SERVICE_INSTANCE_RELATION_CLIENT_P99 = "service_instance_relation_client_p99";
    public static String SERVICE_INSTANCE_RELATION_SERVER_P99 = "service_instance_relation_server_p99";
    public static String[] ALL_SERVICE_INSTANCE_RELATION_CLIENT_METRICS = {
        SERVICE_INSTANCE_RELATION_CLIENT_CPM
    };

    public static String[] ALL_SERVICE_INSTANCE_RELATION_SERVER_METRICS = {
        SERVICE_INSTANCE_RELATION_SERVER_CPM
    };

    public static String METER_INSTANCE_CPU_PERCENTAGE = "meter_oap_instance_cpu_percentage";
    public static String METER_INSTANCE_JVM_MEMORY_BYTES_USED = "meter_oap_instance_jvm_memory_bytes_used";
    public static String METER_INSTANCE_TRACE_COUNT = "meter_oap_instance_trace_count";
    public static String METER_INSTANCE_METRICS_FIRST_AGGREGATION = "meter_oap_instance_metrics_first_aggregation";
    public static String METER_INSTANCE_PERSISTENCE_PREPARE_COUNT = "meter_oap_instance_persistence_prepare_count";
    public static String METER_INSTANCE_PERSISTENCE_EXECUTE_COUNT = "meter_oap_instance_persistence_execute_count";

    public static String[] ALL_SO11Y_LINER_METRICS = {
        METER_INSTANCE_CPU_PERCENTAGE,
        METER_INSTANCE_JVM_MEMORY_BYTES_USED,
        METER_INSTANCE_METRICS_FIRST_AGGREGATION,
        METER_INSTANCE_PERSISTENCE_PREPARE_COUNT,
        METER_INSTANCE_PERSISTENCE_EXECUTE_COUNT 
    };

    public static String[] ALL_ENVOY_LINER_METRICS = {
        "envoy_heap_memory_used",
        "envoy_heap_memory_max_used",
        "envoy_memory_allocated",
        "envoy_memory_allocated_max",
        "envoy_memory_physical_size",
        "envoy_memory_physical_size_max",
        "envoy_total_connections_used",
        "envoy_worker_threads",
        "envoy_worker_threads_max"
    };

    public static String[] ALL_SO11Y_LABELED_METRICS = {
        // Nothing to check for now.
    };
    private String id;
    private String metricsName;

    public static String METER_JVM_MEMORY_MAX = "meter_jvm_memory_max";
    public static String METER_JVM_THREADS_LIVE = "meter_jvm_threads_live";
    public static String METER_PROCESS_FILES_MAX = "meter_process_files_max";
    public static String[] SIMPLE_MICROMETER_METERS = {
        METER_JVM_MEMORY_MAX,
        METER_JVM_THREADS_LIVE,
        METER_PROCESS_FILES_MAX
    };

    public static Map<String, List<String>> SIMPLE_ZABBIX_METERS = ImmutableMap.<String, List<String>>builder()
            .put("meter_agent_system_cpu_util", Arrays.asList("idle"))
            .put("meter_agent_vm_memory_size", Arrays.asList("total"))
            .put("meter_agent_vfs_fs_size", Arrays.asList("/-total"))
            .build();

    public static String[] SIMPLE_PROM_VM_METERS = {
        "meter_vm_memory_used",
        "meter_vm_memory_total",
        "meter_vm_memory_available",
        "meter_vm_disk_written",
        "meter_vm_network_transmit",
        "meter_vm_tcp_curr_estab",
        "meter_vm_tcp_alloc",
        "meter_vm_sockets_used",
        "meter_vm_udp_inuse",
        "meter_vm_filefd_allocated"
    };

    public static Map<String, List<String>> SIMPLE_PROM_VM_LABELED_METERS = ImmutableMap.<String, List<String>>builder()
        .put("meter_vm_cpu_average_used", Arrays.asList("idle"))
        .put("meter_vm_filesystem_percentage", Arrays.asList("/etc/hosts"))
        .build();
}

