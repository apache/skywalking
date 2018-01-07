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

package org.apache.skywalking.apm.collector.analysis.metric.define.graph;

/**
 * @author peng-yongsheng
 */
public class MetricWorkerIdDefine {
    public static final int SERVICE_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4100;
    public static final int SERVICE_REFERENCE_MINUTE_METRIC_REMOTE_WORKER_ID = 4101;
    public static final int SERVICE_REFERENCE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4102;
    public static final int SERVICE_REFERENCE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4103;
    public static final int SERVICE_REFERENCE_HOUR_METRIC_TRANSFORM_NODE_ID = 4104;
    public static final int SERVICE_REFERENCE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4105;
    public static final int SERVICE_REFERENCE_DAY_METRIC_TRANSFORM_NODE_ID = 4106;
    public static final int SERVICE_REFERENCE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4107;
    public static final int SERVICE_REFERENCE_MONTH_METRIC_TRANSFORM_NODE_ID = 4108;

    public static final int INSTANCE_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4200;
    public static final int INSTANCE_REFERENCE_MINUTE_METRIC_REMOTE_WORKER_ID = 4201;
    public static final int INSTANCE_REFERENCE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4202;
    public static final int INSTANCE_REFERENCE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4203;
    public static final int INSTANCE_REFERENCE_HOUR_METRIC_TRANSFORM_NODE_ID = 4204;
    public static final int INSTANCE_REFERENCE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4205;
    public static final int INSTANCE_REFERENCE_DAY_METRIC_TRANSFORM_NODE_ID = 4206;
    public static final int INSTANCE_REFERENCE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4207;
    public static final int INSTANCE_REFERENCE_MONTH_METRIC_TRANSFORM_NODE_ID = 4208;

    public static final int APPLICATION_REFERENCE_METRIC_AGGREGATION_WORKER_ID = 406;
    public static final int APPLICATION_REFERENCE_METRIC_REMOTE_WORKER_ID = 407;
    public static final int APPLICATION_REFERENCE_METRIC_PERSISTENCE_WORKER_ID = 408;

    public static final int SERVICE_METRIC_AGGREGATION_WORKER_ID = 409;
    public static final int SERVICE_METRIC_REMOTE_WORKER_ID = 410;
    public static final int SERVICE_METRIC_PERSISTENCE_WORKER_ID = 411;

    public static final int INSTANCE_METRIC_AGGREGATION_WORKER_ID = 412;
    public static final int INSTANCE_METRIC_REMOTE_WORKER_ID = 413;
    public static final int INSTANCE_METRIC_PERSISTENCE_WORKER_ID = 414;

    public static final int APPLICATION_METRIC_AGGREGATION_WORKER_ID = 415;
    public static final int APPLICATION_METRIC_REMOTE_WORKER_ID = 416;
    public static final int APPLICATION_METRIC_PERSISTENCE_WORKER_ID = 417;

    public static final int APPLICATION_COMPONENT_AGGREGATION_WORKER_ID = 418;
    public static final int APPLICATION_COMPONENT_REMOTE_WORKER_ID = 419;
    public static final int APPLICATION_COMPONENT_PERSISTENCE_WORKER_ID = 420;

    public static final int APPLICATION_MAPPING_AGGREGATION_WORKER_ID = 421;
    public static final int APPLICATION_MAPPING_REMOTE_WORKER_ID = 422;
    public static final int APPLICATION_MAPPING_PERSISTENCE_WORKER_ID = 423;

    public static final int SERVICE_ENTRY_AGGREGATION_WORKER_ID = 424;
    public static final int SERVICE_ENTRY_REMOTE_WORKER_ID = 425;
    public static final int SERVICE_ENTRY_PERSISTENCE_WORKER_ID = 426;

    public static final int GLOBAL_TRACE_PERSISTENCE_WORKER_ID = 427;
    public static final int SEGMENT_COST_PERSISTENCE_WORKER_ID = 428;

    public static final int INSTANCE_REFERENCE_GRAPH_BRIDGE_WORKER_ID = 429;
    public static final int APPLICATION_REFERENCE_GRAPH_BRIDGE_WORKER_ID = 430;
    public static final int SERVICE_METRIC_GRAPH_BRIDGE_WORKER_ID = 431;
    public static final int INSTANCE_METRIC_GRAPH_BRIDGE_WORKER_ID = 432;
    public static final int APPLICATION_METRIC_GRAPH_BRIDGE_WORKER_ID = 433;

    public static final int INSTANCE_MAPPING_AGGREGATION_WORKER_ID = 434;
    public static final int INSTANCE_MAPPING_REMOTE_WORKER_ID = 435;
    public static final int INSTANCE_MAPPING_PERSISTENCE_WORKER_ID = 436;
}
