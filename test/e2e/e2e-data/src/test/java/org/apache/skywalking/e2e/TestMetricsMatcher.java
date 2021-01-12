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

package org.apache.skywalking.e2e;

import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsValue;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestMetricsMatcher {
    @Test
    public void shouldVerifyOneOf() {
        Metrics metrics = new Metrics();
        metrics.getValues().add(new MetricsValue().setValue("12"));
        AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
        MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
        greaterThanZero.setValue("gt 0");
        instanceRespTimeMatcher.setValue(greaterThanZero);
        instanceRespTimeMatcher.verify(metrics);
    }

    @Test
    public void shouldFailedVerifyOneOf() {
        assertThrows(AssertionError.class, () -> {
            Metrics metrics = new Metrics();
            metrics.getValues().add(new MetricsValue().setValue("0"));
            AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(metrics);
        });
    }
}
