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

package org.apache.skywalking.oap.server.analyzer.provider.trace;

import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleProvider;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.TraceSegmentSampler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class TraceSegmentSamplerTest {
    private AnalyzerModuleProvider provider;
    private CustomTraceSampleRateWatcher customTraceSampleRateWatcher;
    private TraceSampleRateWatcher traceSampleRateWatcher;

    @Before
    public void init() throws Exception {
        provider = new AnalyzerModuleProvider();
        customTraceSampleRateWatcher = new CustomTraceSampleRateWatcher(provider);
        CustomTraceSampleRateWatcher.ServiceInfos serviceInfos
                = Whitebox.invokeMethod(customTraceSampleRateWatcher, "parseFromFile", "custom-trace-sample-rate.yml");
        Assert.assertEquals(2, serviceInfos.getServices().size());
        traceSampleRateWatcher = new TraceSampleRateWatcher(provider);
    }

    @Test
    public void shouldSample() {
        SegmentObject segmentObject = SegmentObject.newBuilder().setService("serverName1").build();
        TraceSegmentSampler segmentSampler = new TraceSegmentSampler(traceSampleRateWatcher, customTraceSampleRateWatcher);
        int duration = 12000;
        Assert.assertTrue(segmentSampler.shouldSample(segmentObject, duration));
    }

}
