#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This script relies on few environment variables to determine source code package
# behavior, those variables are:
#   RELEASE_VERSION -- The version of this source package.
# For example: RELEASE_VERSION=5.0.0-alpha

NAME_MAPPINGS=$(cat <<EOF
browser_error_log_in_latency=browser_error_log_in_latency_seconds
browser_perf_data_in_latency=browser_perf_data_in_latency_seconds
envoy_als_in_latency=envoy_als_in_latency_seconds
envoy_metric_in_latency=envoy_metric_in_latency_seconds
event_in_latency=event_in_latency_seconds
graphql_query_latency=graphql_query_latency_seconds
k8s_als_in_latency=k8s_als_in_latency_seconds
log_in_latency=log_in_latency_seconds
mesh_analysis_latency=mesh_analysis_latency_seconds
meter_batch_in_latency=meter_batch_in_latency_seconds
meter_in_latency=meter_in_latency_seconds
otel_logs_latency=otel_logs_latency_seconds
otel_metrics_latency=otel_metrics_latency_seconds
otel_spans_latency=otel_spans_latency_seconds
persistence_timer_bulk_all_latency=persistence_timer_bulk_all_latency_seconds
persistence_timer_bulk_execute_latency=persistence_timer_bulk_execute_latency_seconds
persistence_timer_bulk_prepare_latency=persistence_timer_bulk_prepare_latency_seconds
profile_task_in_latency=profile_task_in_latency_seconds
remote_in_latency=remote_in_latency_seconds
telegraf_in_latency=telegraf_in_latency_seconds
trace_in_latency=trace_in_latency_seconds
EOF
)

while IFS= read -r line; do
    IFS='=' read -r original_name new_name description <<< "$line"

    find . -type f \( -name "*.yaml" -o -name "*.yml" -o -name "*.java" -o -name "*.json" \) \
        | xargs -I{} sed -i '' "s/$original_name/$new_name/g" {};

done <<< "$NAME_MAPPINGS"
