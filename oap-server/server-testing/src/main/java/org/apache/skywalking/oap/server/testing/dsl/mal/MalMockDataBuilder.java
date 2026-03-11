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
 */

package org.apache.skywalking.oap.server.testing.dsl.mal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds version-neutral {@link MalMockSample} lists from
 * the {@code input} section of a MAL {@code .data.yaml} companion file.
 *
 * <p>The caller converts the result to v1 or v2 {@code SampleFamily} types
 * using a simple adapter (typically 3-5 lines).
 */
public final class MalMockDataBuilder {

    private MalMockDataBuilder() {
    }

    /**
     * Parses the {@code input} section of a {@code .data.yaml} file into
     * version-neutral sample lists, keyed by metric name.
     *
     * <p>Expected YAML structure:
     * <pre>
     * input:
     *   metric_name:
     *     - labels:
     *         label1: value1
     *       value: 100.0
     * </pre>
     *
     * @param inputSection the parsed "input" map from YAML
     * @param valueScale   multiplier for sample values (e.g., 0.5 for priming)
     * @return samples grouped by metric name
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<MalMockSample>> buildFromInput(
            final Map<String, Object> inputSection,
            final double valueScale) {
        final Map<String, List<MalMockSample>> result = new LinkedHashMap<>();
        if (inputSection == null) {
            return result;
        }

        for (final Map.Entry<String, Object> entry : inputSection.entrySet()) {
            final String metricName = entry.getKey();
            final Object samplesObj = entry.getValue();
            if (!(samplesObj instanceof List)) {
                continue;
            }

            final List<MalMockSample> samples = new ArrayList<>();
            for (final Object sampleObj : (List<?>) samplesObj) {
                if (!(sampleObj instanceof Map)) {
                    continue;
                }
                final Map<String, Object> sampleMap =
                    (Map<String, Object>) sampleObj;

                // Parse labels
                final Map<String, String> labels = new LinkedHashMap<>();
                final Object labelsObj = sampleMap.get("labels");
                if (labelsObj instanceof Map) {
                    for (final Map.Entry<?, ?> le :
                            ((Map<?, ?>) labelsObj).entrySet()) {
                        labels.put(String.valueOf(le.getKey()),
                            String.valueOf(le.getValue()));
                    }
                }

                // Parse value
                final Object valueObj = sampleMap.get("value");
                final double value;
                if (valueObj instanceof Number) {
                    value = ((Number) valueObj).doubleValue() * valueScale;
                } else if (valueObj != null) {
                    value = Double.parseDouble(String.valueOf(valueObj))
                        * valueScale;
                } else {
                    value = 0.0;
                }

                samples.add(new MalMockSample(labels, value));
            }

            if (!samples.isEmpty()) {
                result.put(metricName, samples);
            }
        }
        return result;
    }
}
