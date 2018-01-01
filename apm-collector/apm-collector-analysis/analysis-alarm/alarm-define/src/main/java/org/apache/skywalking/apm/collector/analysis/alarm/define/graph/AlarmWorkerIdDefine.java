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
    public static final int SERVICE_METRIC_ALARM_ASSERT_WORKER_ID = 500;
    public static final int SERVICE_METRIC_TRANSFORM_GRAPH_BRIDGE_WORKER_ID = 501;
    public static final int SERVICE_METRIC_ALARM_REMOTE_WORKER_ID = 500;
    public static final int SERVICE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 500;
    public static final int SERVICE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 500;
    public static final int SERVICE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 500;

    public static final int INSTANCE_METRIC_TRANSFORM_WORKER_ID = 502;
    public static final int INSTANCE_METRIC_TRANSFORM_GRAPH_BRIDGE_WORKER_ID = 503;
    public static final int APPLICATION_METRIC_TRANSFORM_WORKER_ID = 504;
    public static final int APPLICATION_METRIC_TRANSFORM_GRAPH_BRIDGE_WORKER_ID = 505;
    public static final int ALARM_METRIC_REMOTE_WORKER_ID = 506;
    public static final int ALARM_METRIC_APPLICATION_METRIC_TRANSFORM_GRAPH_BRIDGE_WORKER_ID = 507;
    public static final int ALARM_METRIC_INSTANCE_METRIC_TRANSFORM_GRAPH_BRIDGE_WORKER_ID = 508;
    public static final int ALARM_METRIC_SERVICE_METRIC_TRANSFORM_GRAPH_BRIDGE_WORKER_ID = 509;
    public static final int ALARM_METRIC_AGGREGATION_WORKER_ID = 510;
}
