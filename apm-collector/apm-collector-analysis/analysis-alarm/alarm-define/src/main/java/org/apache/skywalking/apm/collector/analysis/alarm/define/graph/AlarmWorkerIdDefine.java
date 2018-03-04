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

package org.apache.skywalking.apm.collector.analysis.alarm.define.graph;

/**
 * @author peng-yongsheng
 */
public class AlarmWorkerIdDefine {
    public static final int SERVICE_METRIC_ALARM_ASSERT_WORKER_ID = 5000;
    public static final int SERVICE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 5001;
    public static final int SERVICE_METRIC_ALARM_REMOTE_WORKER_ID = 5002;
    public static final int SERVICE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 5003;
    public static final int SERVICE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 5004;
    public static final int SERVICE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 5005;

    public static final int INSTANCE_METRIC_ALARM_ASSERT_WORKER_ID = 5010;
    public static final int INSTANCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 5011;
    public static final int INSTANCE_METRIC_ALARM_REMOTE_WORKER_ID = 5012;
    public static final int INSTANCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 5013;
    public static final int INSTANCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 5014;
    public static final int INSTANCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 5015;

    public static final int APPLICATION_METRIC_ALARM_ASSERT_WORKER_ID = 5020;
    public static final int APPLICATION_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 5021;
    public static final int APPLICATION_METRIC_ALARM_REMOTE_WORKER_ID = 5022;
    public static final int APPLICATION_METRIC_ALARM_PERSISTENCE_WORKER_ID = 5023;
    public static final int APPLICATION_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 5024;

    public static final int SERVICE_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID = 5030;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 5031;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_REMOTE_WORKER_ID = 5032;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 5033;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 5034;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 5035;

    public static final int INSTANCE_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID = 5040;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 5041;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_REMOTE_WORKER_ID = 5042;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 5043;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 5044;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 5045;

    public static final int APPLICATION_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID = 5050;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 5051;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_REMOTE_WORKER_ID = 5052;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 5053;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 5054;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 5055;

    public static final int APPLICATION_METRIC_ALARM_LIST_MINUTE_PERSISTENCE_WORKER_ID = 5051;
    public static final int APPLICATION_METRIC_ALARM_LIST_HOUR_PERSISTENCE_WORKER_ID = 5052;
    public static final int APPLICATION_METRIC_ALARM_LIST_DAY_PERSISTENCE_WORKER_ID = 5053;
    public static final int APPLICATION_METRIC_ALARM_LIST_MONTH_PERSISTENCE_WORKER_ID = 5054;
    public static final int APPLICATION_METRIC_ALARM_LIST_HOUR_TRANSFORM_NODE_ID = 5055;
    public static final int APPLICATION_METRIC_ALARM_LIST_DAY_TRANSFORM_NODE_ID = 5056;
    public static final int APPLICATION_METRIC_ALARM_LIST_MONTH_TRANSFORM_NODE_ID = 5057;
}
