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
    public static final int SERVICE_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4010;
    public static final int SERVICE_REFERENCE_MINUTE_METRIC_REMOTE_WORKER_ID = 4011;
    public static final int SERVICE_REFERENCE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4012;
    public static final int SERVICE_REFERENCE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4013;
    public static final int SERVICE_REFERENCE_HOUR_METRIC_TRANSFORM_NODE_ID = 4014;
    public static final int SERVICE_REFERENCE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4015;
    public static final int SERVICE_REFERENCE_DAY_METRIC_TRANSFORM_NODE_ID = 4016;
    public static final int SERVICE_REFERENCE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4017;
    public static final int SERVICE_REFERENCE_MONTH_METRIC_TRANSFORM_NODE_ID = 4018;

    public static final int INSTANCE_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4020;
    public static final int INSTANCE_REFERENCE_MINUTE_METRIC_REMOTE_WORKER_ID = 4021;
    public static final int INSTANCE_REFERENCE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4022;
    public static final int INSTANCE_REFERENCE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4023;
    public static final int INSTANCE_REFERENCE_HOUR_METRIC_TRANSFORM_NODE_ID = 4024;
    public static final int INSTANCE_REFERENCE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4025;
    public static final int INSTANCE_REFERENCE_DAY_METRIC_TRANSFORM_NODE_ID = 4026;
    public static final int INSTANCE_REFERENCE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4027;
    public static final int INSTANCE_REFERENCE_MONTH_METRIC_TRANSFORM_NODE_ID = 4028;

    public static final int APPLICATION_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4030;
    public static final int APPLICATION_REFERENCE_MINUTE_METRIC_REMOTE_WORKER_ID = 4031;
    public static final int APPLICATION_REFERENCE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4032;
    public static final int APPLICATION_REFERENCE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4033;
    public static final int APPLICATION_REFERENCE_HOUR_METRIC_TRANSFORM_NODE_ID = 4034;
    public static final int APPLICATION_REFERENCE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4035;
    public static final int APPLICATION_REFERENCE_DAY_METRIC_TRANSFORM_NODE_ID = 4036;
    public static final int APPLICATION_REFERENCE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4037;
    public static final int APPLICATION_REFERENCE_MONTH_METRIC_TRANSFORM_NODE_ID = 4038;

    public static final int SERVICE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4400;
    public static final int SERVICE_MINUTE_METRIC_REMOTE_WORKER_ID = 4401;
    public static final int SERVICE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4402;
    public static final int SERVICE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4403;
    public static final int SERVICE_HOUR_METRIC_TRANSFORM_NODE_ID = 4404;
    public static final int SERVICE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4405;
    public static final int SERVICE_DAY_METRIC_TRANSFORM_NODE_ID = 4406;
    public static final int SERVICE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4407;
    public static final int SERVICE_MONTH_METRIC_TRANSFORM_NODE_ID = 4408;

    public static final int INSTANCE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4500;
    public static final int INSTANCE_MINUTE_METRIC_REMOTE_WORKER_ID = 4501;
    public static final int INSTANCE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4502;
    public static final int INSTANCE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4503;
    public static final int INSTANCE_HOUR_METRIC_TRANSFORM_NODE_ID = 4504;
    public static final int INSTANCE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4505;
    public static final int INSTANCE_DAY_METRIC_TRANSFORM_NODE_ID = 4506;
    public static final int INSTANCE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4507;
    public static final int INSTANCE_MONTH_METRIC_TRANSFORM_NODE_ID = 4508;

    public static final int APPLICATION_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4600;
    public static final int APPLICATION_MINUTE_METRIC_REMOTE_WORKER_ID = 4601;
    public static final int APPLICATION_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4602;
    public static final int APPLICATION_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4603;
    public static final int APPLICATION_HOUR_METRIC_TRANSFORM_NODE_ID = 4604;
    public static final int APPLICATION_DAY_METRIC_PERSISTENCE_WORKER_ID = 4605;
    public static final int APPLICATION_DAY_METRIC_TRANSFORM_NODE_ID = 4606;
    public static final int APPLICATION_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4607;
    public static final int APPLICATION_MONTH_METRIC_TRANSFORM_NODE_ID = 4608;

    public static final int INSTANCE_MAPPING_MINUTE_AGGREGATION_WORKER_ID = 4700;
    public static final int INSTANCE_MAPPING_MINUTE_REMOTE_WORKER_ID = 4701;
    public static final int INSTANCE_MAPPING_MINUTE_PERSISTENCE_WORKER_ID = 4702;
    public static final int INSTANCE_MAPPING_HOUR_PERSISTENCE_WORKER_ID = 4703;
    public static final int INSTANCE_MAPPING_HOUR_TRANSFORM_NODE_ID = 4704;
    public static final int INSTANCE_MAPPING_DAY_PERSISTENCE_WORKER_ID = 4705;
    public static final int INSTANCE_MAPPING_DAY_TRANSFORM_NODE_ID = 4706;
    public static final int INSTANCE_MAPPING_MONTH_PERSISTENCE_WORKER_ID = 4707;
    public static final int INSTANCE_MAPPING_MONTH_TRANSFORM_NODE_ID = 4708;

    public static final int APPLICATION_MAPPING_MINUTE_AGGREGATION_WORKER_ID = 4800;
    public static final int APPLICATION_MAPPING_MINUTE_REMOTE_WORKER_ID = 4801;
    public static final int APPLICATION_MAPPING_MINUTE_PERSISTENCE_WORKER_ID = 4802;
    public static final int APPLICATION_MAPPING_HOUR_PERSISTENCE_WORKER_ID = 4803;
    public static final int APPLICATION_MAPPING_HOUR_TRANSFORM_NODE_ID = 4804;
    public static final int APPLICATION_MAPPING_DAY_PERSISTENCE_WORKER_ID = 4805;
    public static final int APPLICATION_MAPPING_DAY_TRANSFORM_NODE_ID = 4806;
    public static final int APPLICATION_MAPPING_MONTH_PERSISTENCE_WORKER_ID = 4807;
    public static final int APPLICATION_MAPPING_MONTH_TRANSFORM_NODE_ID = 4808;

    public static final int APPLICATION_COMPONENT_MINUTE_AGGREGATION_WORKER_ID = 4900;
    public static final int APPLICATION_COMPONENT_MINUTE_REMOTE_WORKER_ID = 4901;
    public static final int APPLICATION_COMPONENT_MINUTE_PERSISTENCE_WORKER_ID = 4902;
    public static final int APPLICATION_COMPONENT_HOUR_PERSISTENCE_WORKER_ID = 4903;
    public static final int APPLICATION_COMPONENT_HOUR_TRANSFORM_NODE_ID = 4904;
    public static final int APPLICATION_COMPONENT_DAY_PERSISTENCE_WORKER_ID = 4905;
    public static final int APPLICATION_COMPONENT_DAY_TRANSFORM_NODE_ID = 4906;
    public static final int APPLICATION_COMPONENT_MONTH_PERSISTENCE_WORKER_ID = 4907;
    public static final int APPLICATION_COMPONENT_MONTH_TRANSFORM_NODE_ID = 4908;

    public static final int GLOBAL_TRACE_PERSISTENCE_WORKER_ID = 427;
    public static final int SEGMENT_DURATION_PERSISTENCE_WORKER_ID = 428;

    public static final int INSTANCE_REFERENCE_GRAPH_BRIDGE_WORKER_ID = 429;
    public static final int APPLICATION_REFERENCE_GRAPH_BRIDGE_WORKER_ID = 430;
    public static final int SERVICE_METRIC_GRAPH_BRIDGE_WORKER_ID = 431;
    public static final int INSTANCE_METRIC_GRAPH_BRIDGE_WORKER_ID = 432;
    public static final int APPLICATION_METRIC_GRAPH_BRIDGE_WORKER_ID = 433;

    public static final int INST_HEART_BEAT_PERSISTENCE_WORKER_ID = 400;
}
