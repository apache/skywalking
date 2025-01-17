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

package org.apache.skywalking.oap.server.baseline.service;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PredictServiceMetrics {
    private String serviceName;
    private Map<String, List<PredictMetricsValue>> metricsValues;

    @Data
    @Builder
    public static class PredictMetricsValue {
        private long timeBucket;
        private PredictSingleValue singleValue;
        private List<PredictLabelValue> labeledValue;
    }

    @Data
    @Builder
    public static class PredictSingleValue {
        private long value;
        private long upperValue;
        private long lowerValue;
    }

    @Data
    @Builder
    public static class PredictLabelValue {
        private Map<String, String> labels;
        private PredictSingleValue value;
    }
}
