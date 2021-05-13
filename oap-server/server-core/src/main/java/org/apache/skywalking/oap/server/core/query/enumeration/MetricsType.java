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

package org.apache.skywalking.oap.server.core.query.enumeration;

/**
 * @since 8.0.0
 */
public enum MetricsType {
    UNKNOWN,
    // Regular value type is suitable for readMetricsValue, readMetricsValues and sortMetrics
    REGULAR_VALUE,
    // Metrics value includes multiple labels, is suitable for readLabeledMetricsValues
    // Label should be assigned before the query happens, such as at the setting stage
    LABELED_VALUE,
    // Heatmap value suitable for readHeatMap
    HEATMAP,
    // Top metrics is for readSampledRecords only.
    SAMPLED_RECORD
}
