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

package org.apache.skywalking.apm.collector.analysis.jvm.define.graph;

/**
 * @author peng-yongsheng
 */
public class WorkerIdDefine {
    public static final int CPU_METRIC_BRIDGE_NODE_ID = 3000;
    public static final int CPU_SECOND_METRIC_PERSISTENCE_WORKER_ID = 3001;
    public static final int CPU_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 3002;
    public static final int CPU_MINUTE_METRIC_TRANSFORM_NODE_ID = 3003;
    public static final int CPU_HOUR_METRIC_PERSISTENCE_WORKER_ID = 3004;
    public static final int CPU_HOUR_METRIC_TRANSFORM_NODE_ID = 3005;
    public static final int CPU_DAY_METRIC_PERSISTENCE_WORKER_ID = 3006;
    public static final int CPU_DAY_METRIC_TRANSFORM_NODE_ID = 3007;
    public static final int CPU_MONTH_METRIC_PERSISTENCE_WORKER_ID = 3008;
    public static final int CPU_MONTH_METRIC_TRANSFORM_NODE_ID = 3009;

    public static final int GC_METRIC_BRIDGE_NODE_ID = 3100;
    public static final int GC_SECOND_METRIC_PERSISTENCE_WORKER_ID = 3101;
    public static final int GC_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 3102;
    public static final int GC_MINUTE_METRIC_TRANSFORM_NODE_ID = 3103;
    public static final int GC_HOUR_METRIC_PERSISTENCE_WORKER_ID = 3104;
    public static final int GC_HOUR_METRIC_TRANSFORM_NODE_ID = 3105;
    public static final int GC_DAY_METRIC_PERSISTENCE_WORKER_ID = 3106;
    public static final int GC_DAY_METRIC_TRANSFORM_NODE_ID = 3107;
    public static final int GC_MONTH_METRIC_PERSISTENCE_WORKER_ID = 3108;
    public static final int GC_MONTH_METRIC_TRANSFORM_NODE_ID = 3109;

    public static final int MEMORY_METRIC_BRIDGE_NODE_ID = 3200;
    public static final int MEMORY_SECOND_METRIC_PERSISTENCE_WORKER_ID = 3201;
    public static final int MEMORY_MINUTE_METRIC_PERSISTENCE_WORKER_ID = 3202;
    public static final int MEMORY_MINUTE_METRIC_TRANSFORM_NODE_ID = 3203;
    public static final int MEMORY_HOUR_METRIC_PERSISTENCE_WORKER_ID = 3204;
    public static final int MEMORY_HOUR_METRIC_TRANSFORM_NODE_ID = 3205;
    public static final int MEMORY_DAY_METRIC_PERSISTENCE_WORKER_ID = 3206;
    public static final int MEMORY_DAY_METRIC_TRANSFORM_NODE_ID = 3207;
    public static final int MEMORY_MONTH_METRIC_PERSISTENCE_WORKER_ID = 3208;
    public static final int MEMORY_MONTH_METRIC_TRANSFORM_NODE_ID = 3209;

    public static final int INST_HEART_BEAT_PERSISTENCE_WORKER_ID = 302;
    public static final int MEMORY_POOL_METRIC_PERSISTENCE_WORKER_ID = 303;
}
