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

package org.apache.skywalking.apm.meter.micrometer;

import io.micrometer.core.instrument.DistributionSummary;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.List;

public class SkywalkingDistributionSummaryTest extends SkywalkingMeterBaseTest {

    @Test
    public void testSimple() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final DistributionSummary summary = registry.summary("test_simple_distribution_summary", "skywalking", "test");

        Assert.assertTrue(summary instanceof SkywalkingDistributionSummary);
        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));

        summary.record(10d);
        summary.record(13d);
        summary.record(2d);

        // micrometer data
        Assert.assertEquals(3, summary.count());
        Assert.assertEquals(25d, summary.totalAmount(), 0.0);
        Assert.assertEquals(13d, summary.max(), 0.0);

        // original data
        assertCounter(Whitebox.getInternalState(summary, "counter"), "test_simple_distribution_summary_count", tags, 3d);
        assertCounter(Whitebox.getInternalState(summary, "sum"), "test_simple_distribution_summary_sum", tags, 25d);
        assertGauge(Whitebox.getInternalState(summary, "max"), "test_simple_distribution_summary_max", tags, 13d);
        assertHistogramNull(Whitebox.getInternalState(summary, "histogram"));
        assertPercentileNull(Whitebox.getInternalState(summary, "percentile"));
    }

    @Test
    public void testComplex() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final DistributionSummary summary = DistributionSummary.builder("test_complex_distribution_summary")
            .tags("skywalking", "test")
            .publishPercentiles(0.5, 0.95)
            .serviceLevelObjectives(10, 20)
            .minimumExpectedValue(1d)
            .register(registry);

        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));

        summary.record(10d);
        summary.record(13d);
        summary.record(2d);

        // micrometer data
        Assert.assertEquals(3, summary.count());
        Assert.assertEquals(25d, summary.totalAmount(), 0.0);
        Assert.assertEquals(13d, summary.max(), 0.0);

        // original data
        assertCounter(Whitebox.getInternalState(summary, "counter"), "test_complex_distribution_summary_count", tags, 3d);
        assertCounter(Whitebox.getInternalState(summary, "sum"), "test_complex_distribution_summary_sum", tags, 25d);
        assertGauge(Whitebox.getInternalState(summary, "max"), "test_complex_distribution_summary_max", tags, 13d);
        assertHistogram(Whitebox.getInternalState(summary, "histogram"), "test_complex_distribution_summary_histogram", tags, 1, 1, 10, 2, 20, 0);
        assertPercentile(Whitebox.getInternalState(summary, "percentile"), "test_complex_distribution_summary_percentile", tags, 10, 1, 13, 1, 2, 1);
    }
}
