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

import org.apache.skywalking.e2e.AbstractQuery;

/**
 * @author kezhenxu94
 */
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

    private String id;
    private String metricsName;

    public String id() {
        return id;
    }

    public MetricsQuery id(String id) {
        this.id = id;
        return this;
    }

    public String metricsName() {
        return metricsName;
    }

    public MetricsQuery metricsName(String metricsName) {
        this.metricsName = metricsName;
        return this;
    }

    @Override
    public String toString() {
        return "MetricsQuery{" +
            "id='" + id + '\'' +
            ", metricsName='" + metricsName + '\'' +
            '}';
    }
}
