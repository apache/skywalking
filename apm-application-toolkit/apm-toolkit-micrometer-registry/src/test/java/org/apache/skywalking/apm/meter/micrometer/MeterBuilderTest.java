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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.apache.skywalking.apm.toolkit.meter.Histogram;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MeterBuilderTest {

    @Test
    public void testBuildHistogram() {
        final MeterId meterId = new MeterId("test", MeterId.MeterType.COUNTER,
            Arrays.asList(new MeterId.Tag("k1", "v1")));

        // Build a new distribution config
        final DistributionStatisticConfig statisticConfig = DistributionStatisticConfig.builder()
            .serviceLevelObjectives(Duration.ofMillis(1).toNanos(), Duration.ofMillis(5).toNanos(), Duration.ofMillis(10).toNanos())
            .minimumExpectedValue(0d).build();

        // Check histogram
        final Optional<Histogram> histogramOptional = MeterBuilder.buildHistogram(meterId, true, statisticConfig, true);
        final Histogram histogram = histogramOptional.orElse(null);
        Assert.assertNotNull(histogram);

        // Don't need the histogram
        Assert.assertNull(MeterBuilder.buildHistogram(meterId, true, DistributionStatisticConfig.DEFAULT, true).orElse(null));
    }

    @Test
    public void testConvertId() {
        final List<MeterId.Tag> meterTags = Arrays.asList(new MeterId.Tag("k1", "v1"));

        // Counter type check
        final Meter.Id counterId = new Meter.Id("test", Tags.of("k1", "v1"), null, "test", Meter.Type.COUNTER);
        assertId(MeterBuilder.convertId(counterId, "test"), "test", MeterId.MeterType.COUNTER, meterTags);

        // Gauge type check
        final Meter.Id gaugeId = new Meter.Id("test", Tags.of("k1", "v1"), null, "test", Meter.Type.GAUGE);
        assertId(MeterBuilder.convertId(gaugeId, "test"), "test", MeterId.MeterType.GAUGE, meterTags);

        // Histogram type check
        final Meter.Id otherId = new Meter.Id("test", Tags.of("k1", "v1"), null, "test", Meter.Type.DISTRIBUTION_SUMMARY);
        assertId(MeterBuilder.convertId(otherId, "test"), "test", MeterId.MeterType.HISTOGRAM, meterTags);
    }

    /**
     * Assert the meter id
     */
    private void assertId(MeterId meterId, String name, MeterId.MeterType type, List<MeterId.Tag> tags) {
        Assert.assertEquals(name, meterId.getName());
        Assert.assertEquals(type, meterId.getType());
        Assert.assertEquals(tags, meterId.getTags());
    }

}
