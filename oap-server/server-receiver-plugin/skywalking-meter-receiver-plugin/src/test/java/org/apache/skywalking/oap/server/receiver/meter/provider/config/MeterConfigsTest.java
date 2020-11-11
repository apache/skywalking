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

package org.apache.skywalking.oap.server.receiver.meter.provider.config;

import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MeterConfigsTest {
    private static final String CONFIG_PATH = "meter-analyzer-config";

    @Test
    public void testLoadConfig() throws ModuleStartException {
        final List<MeterConfig> meterConfigs = MeterConfigs.loadConfig(CONFIG_PATH, new String[] {"config.yaml"});

        Assert.assertEquals(3, meterConfigs.size());

        // check meter1
        final MeterConfig conf1 = meterConfigs.get(0);
        Assert.assertEquals("build_test1", conf1.getName());
        Assert.assertEquals(ScopeType.SERVICE, conf1.getScope().getType());
        Assert.assertEquals("avg", conf1.getMeter().getOperation());
        Assert.assertEquals("meter[\"test_count1\"].tagFilter(\"k1\", \"v1\").scale(2)", conf1.getMeter().getValue());

        // check meter2
        final MeterConfig conf2 = meterConfigs.get(1);
        Assert.assertEquals("build_test2", conf2.getName());
        Assert.assertEquals(ScopeType.SERVICE_INSTANCE, conf2.getScope().getType());
        Assert.assertEquals("avgHistogram", conf2.getMeter().getOperation());
        Assert.assertEquals("meter[\"test_histogram\"]", conf2.getMeter().getValue());

        // check meter3
        final MeterConfig conf3 = meterConfigs.get(2);
        Assert.assertEquals("build_test3", conf3.getName());
        Assert.assertEquals(ScopeType.ENDPOINT, conf3.getScope().getType());
        Assert.assertEquals("test_endpoint", conf3.getScope().getEndpoint());
        Assert.assertEquals("avgHistogramPercentile", conf3.getMeter().getOperation());
        Assert.assertEquals("meter[\"test_histogram\"]", conf3.getMeter().getValue());
        Assert.assertEquals(Arrays.asList(50, 90, 99), conf3.getMeter().getPercentile());
    }

}
