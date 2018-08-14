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

    public static final int SERVICE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4040;
    public static final int SERVICE_MINUTE_METRIC_REMOTE_WORKER_ID = 4041;
    public static final int SERVICE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4042;
    public static final int SERVICE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4043;
    public static final int SERVICE_HOUR_METRIC_TRANSFORM_NODE_ID = 4044;
    public static final int SERVICE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4045;
    public static final int SERVICE_DAY_METRIC_TRANSFORM_NODE_ID = 4046;
    public static final int SERVICE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4047;
    public static final int SERVICE_MONTH_METRIC_TRANSFORM_NODE_ID = 4048;

    public static final int INSTANCE_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4050;
    public static final int INSTANCE_MINUTE_METRIC_REMOTE_WORKER_ID = 4051;
    public static final int INSTANCE_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4052;
    public static final int INSTANCE_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4053;
    public static final int INSTANCE_HOUR_METRIC_TRANSFORM_NODE_ID = 4054;
    public static final int INSTANCE_DAY_METRIC_PERSISTENCE_WORKER_ID = 4055;
    public static final int INSTANCE_DAY_METRIC_TRANSFORM_NODE_ID = 4056;
    public static final int INSTANCE_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4057;
    public static final int INSTANCE_MONTH_METRIC_TRANSFORM_NODE_ID = 4058;

    public static final int APPLICATION_MINUTE_METRIC_AGGREGATION_WORKER_ID = 4060;
    public static final int APPLICATION_MINUTE_METRIC_REMOTE_WORKER_ID = 4061;
    public static final int APPLICATION_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 4062;
    public static final int APPLICATION_HOUR_METRIC_PERSISTENCE_WORKER_ID = 4063;
    public static final int APPLICATION_HOUR_METRIC_TRANSFORM_NODE_ID = 4064;
    public static final int APPLICATION_DAY_METRIC_PERSISTENCE_WORKER_ID = 4065;
    public static final int APPLICATION_DAY_METRIC_TRANSFORM_NODE_ID = 4066;
    public static final int APPLICATION_MONTH_METRIC_PERSISTENCE_WORKER_ID = 4067;
    public static final int APPLICATION_MONTH_METRIC_TRANSFORM_NODE_ID = 4068;

    public static final int INSTANCE_MAPPING_MINUTE_AGGREGATION_WORKER_ID = 4070;
    public static final int INSTANCE_MAPPING_MINUTE_REMOTE_WORKER_ID = 4071;
    public static final int INSTANCE_MAPPING_MINUTE_PERSISTENCE_WORKER_ID = 4072;
    public static final int INSTANCE_MAPPING_HOUR_PERSISTENCE_WORKER_ID = 4073;
    public static final int INSTANCE_MAPPING_HOUR_TRANSFORM_NODE_ID = 4074;
    public static final int INSTANCE_MAPPING_DAY_PERSISTENCE_WORKER_ID = 4075;
    public static final int INSTANCE_MAPPING_DAY_TRANSFORM_NODE_ID = 4076;
    public static final int INSTANCE_MAPPING_MONTH_PERSISTENCE_WORKER_ID = 4077;
    public static final int INSTANCE_MAPPING_MONTH_TRANSFORM_NODE_ID = 4078;

    public static final int APPLICATION_MAPPING_MINUTE_AGGREGATION_WORKER_ID = 4080;
    public static final int APPLICATION_MAPPING_MINUTE_REMOTE_WORKER_ID = 4081;
    public static final int APPLICATION_MAPPING_MINUTE_PERSISTENCE_WORKER_ID = 4082;
    public static final int APPLICATION_MAPPING_HOUR_PERSISTENCE_WORKER_ID = 4083;
    public static final int APPLICATION_MAPPING_HOUR_TRANSFORM_NODE_ID = 4084;
    public static final int APPLICATION_MAPPING_DAY_PERSISTENCE_WORKER_ID = 4085;
    public static final int APPLICATION_MAPPING_DAY_TRANSFORM_NODE_ID = 4086;
    public static final int APPLICATION_MAPPING_MONTH_PERSISTENCE_WORKER_ID = 4087;
    public static final int APPLICATION_MAPPING_MONTH_TRANSFORM_NODE_ID = 4088;

    public static final int APPLICATION_COMPONENT_MINUTE_AGGREGATION_WORKER_ID = 4090;
    public static final int APPLICATION_COMPONENT_MINUTE_REMOTE_WORKER_ID = 4091;
    public static final int APPLICATION_COMPONENT_MINUTE_PERSISTENCE_WORKER_ID = 4092;
    public static final int APPLICATION_COMPONENT_HOUR_PERSISTENCE_WORKER_ID = 4093;
    public static final int APPLICATION_COMPONENT_HOUR_TRANSFORM_NODE_ID = 4094;
    public static final int APPLICATION_COMPONENT_DAY_PERSISTENCE_WORKER_ID = 4095;
    public static final int APPLICATION_COMPONENT_DAY_TRANSFORM_NODE_ID = 4096;
    public static final int APPLICATION_COMPONENT_MONTH_PERSISTENCE_WORKER_ID = 4097;
    public static final int APPLICATION_COMPONENT_MONTH_TRANSFORM_NODE_ID = 4098;

    public static final int RESPONSE_TIME_DISTRIBUTION_MINUTE_AGGREGATION_WORKER_ID = 4100;
    public static final int RESPONSE_TIME_DISTRIBUTION_MINUTE_REMOTE_WORKER_ID = 4101;
    public static final int RESPONSE_TIME_DISTRIBUTION_MINUTE_PERSISTENCE_WORKER_ID = 4102;
    public static final int RESPONSE_TIME_DISTRIBUTION_HOUR_PERSISTENCE_WORKER_ID = 4103;
    public static final int RESPONSE_TIME_DISTRIBUTION_HOUR_TRANSFORM_NODE_ID = 4104;
    public static final int RESPONSE_TIME_DISTRIBUTION_DAY_PERSISTENCE_WORKER_ID = 4105;
    public static final int RESPONSE_TIME_DISTRIBUTION_DAY_TRANSFORM_NODE_ID = 4106;
    public static final int RESPONSE_TIME_DISTRIBUTION_MONTH_PERSISTENCE_WORKER_ID = 4107;
    public static final int RESPONSE_TIME_DISTRIBUTION_MONTH_TRANSFORM_NODE_ID = 4108;

    public static final int GLOBAL_TRACE_PERSISTENCE_WORKER_ID = 4110;
    public static final int SEGMENT_DURATION_PERSISTENCE_WORKER_ID = 4120;

    public static final int INSTANCE_REFERENCE_GRAPH_BRIDGE_WORKER_ID = 4130;
    public static final int APPLICATION_REFERENCE_GRAPH_BRIDGE_WORKER_ID = 4140;
    public static final int SERVICE_METRIC_GRAPH_BRIDGE_WORKER_ID = 4150;
    public static final int INSTANCE_METRIC_GRAPH_BRIDGE_WORKER_ID = 4160;
    public static final int APPLICATION_METRIC_GRAPH_BRIDGE_WORKER_ID = 4170;

    public static final int INST_HEART_BEAT_PERSISTENCE_WORKER_ID = 4180;

    public static final int SERVICE_NAME_HEART_BEAT_AGGREGATION_WORKER_ID = 4190;
    public static final int SERVICE_NAME_HEART_BEAT_REMOTE_WORKER_ID = 4191;
    public static final int SERVICE_NAME_HEART_BEAT_PERSISTENCE_WORKER_ID = 4192;
}
