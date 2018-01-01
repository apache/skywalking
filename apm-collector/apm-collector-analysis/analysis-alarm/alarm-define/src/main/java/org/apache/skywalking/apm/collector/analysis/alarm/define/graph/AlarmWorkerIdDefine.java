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
    public static final int SERVICE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 501;
    public static final int SERVICE_METRIC_ALARM_REMOTE_WORKER_ID = 502;
    public static final int SERVICE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 503;
    public static final int SERVICE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 504;
    public static final int SERVICE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 505;

    public static final int INSTANCE_METRIC_ALARM_ASSERT_WORKER_ID = 510;
    public static final int INSTANCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 511;
    public static final int INSTANCE_METRIC_ALARM_REMOTE_WORKER_ID = 512;
    public static final int INSTANCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 513;
    public static final int INSTANCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 514;
    public static final int INSTANCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 515;

    public static final int APPLICATION_METRIC_ALARM_ASSERT_WORKER_ID = 520;
    public static final int APPLICATION_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 521;
    public static final int APPLICATION_METRIC_ALARM_REMOTE_WORKER_ID = 522;
    public static final int APPLICATION_METRIC_ALARM_PERSISTENCE_WORKER_ID = 523;
    public static final int APPLICATION_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 524;
    public static final int APPLICATION_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 525;

    public static final int SERVICE_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID = 530;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 531;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_REMOTE_WORKER_ID = 532;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 533;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 534;
    public static final int SERVICE_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 535;

    public static final int INSTANCE_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID = 540;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 541;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_REMOTE_WORKER_ID = 542;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 543;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 544;
    public static final int INSTANCE_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 545;

    public static final int APPLICATION_REFERENCE_METRIC_ALARM_ASSERT_WORKER_ID = 550;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID = 551;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_REMOTE_WORKER_ID = 552;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_PERSISTENCE_WORKER_ID = 553;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_LIST_PERSISTENCE_WORKER_ID = 554;
    public static final int APPLICATION_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID = 555;
}
