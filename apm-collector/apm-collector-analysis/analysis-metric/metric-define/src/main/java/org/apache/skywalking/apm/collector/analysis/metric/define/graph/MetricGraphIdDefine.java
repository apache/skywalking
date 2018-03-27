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
public class MetricGraphIdDefine {
    public static final int SERVICE_REFERENCE_METRIC_GRAPH_ID = 400;
    public static final int INSTANCE_REFERENCE_METRIC_GRAPH_ID = 401;
    public static final int APPLICATION_REFERENCE_METRIC_GRAPH_ID = 402;

    public static final int SERVICE_METRIC_GRAPH_ID = 403;
    public static final int INSTANCE_METRIC_GRAPH_ID = 404;
    public static final int APPLICATION_METRIC_GRAPH_ID = 405;

    public static final int APPLICATION_COMPONENT_GRAPH_ID = 406;
    public static final int APPLICATION_MAPPING_GRAPH_ID = 407;
    public static final int SERVICE_MAPPING_GRAPH_ID = 408;
    public static final int GLOBAL_TRACE_GRAPH_ID = 409;
    public static final int SEGMENT_DURATION_GRAPH_ID = 410;
    public static final int INSTANCE_MAPPING_GRAPH_ID = 411;

    public static final int INSTANCE_HEART_BEAT_PERSISTENCE_GRAPH_ID = 412;
}
