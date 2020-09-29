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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
public class MeterProcessorTest extends MeterBaseTest {

    @Test
    public void testRead() {
        // basic info
        Assert.assertEquals("service", processor.service());
        Assert.assertEquals("instance", processor.serviceInstance());
        Assert.assertEquals(timestamp, processor.timestamp().longValue());

        // meters check
        final Map<String, EvalMultipleData> meters = (Map<String, EvalMultipleData>) Whitebox.getInternalState(
            processor, "meters");
        Assert.assertEquals(2, meters.size());

        // single value
        EvalSingleData singleData = verifyBaseData(
            meters.get("test_count1"), "test_count1", Collections.singletonMap("k1", "v1"));
        Assert.assertEquals(1, singleData.getValue(), 0.0);

        // histogram
        EvalHistogramData histogramData = verifyBaseData(
            meters.get("test_histogram"), "test_histogram", Collections.singletonMap("k2", "v2"));
        Assert.assertEquals(3, histogramData.getBuckets().size());
        Assert.assertEquals(10, histogramData.getBuckets().get(1d).longValue());
        Assert.assertEquals(15, histogramData.getBuckets().get(5d).longValue());
        Assert.assertEquals(3, histogramData.getBuckets().get(10d).longValue());
    }

    @Test
    public void testProcess() {
        // each builder has build and send
        MeterProcessService context = (MeterProcessService) Whitebox.getInternalState(processor, "processService");
        List<MeterBuilder> builders = context.enabledBuilders()
                                             .stream()
                                             .map(Mockito::spy)
                                             .peek(builder -> doNothing().when(builder).buildAndSend(any(), any()))
                                             .collect(Collectors.toList());
        Whitebox.setInternalState(context, "meterBuilders", builders);
        processor.process();
        builders.stream().forEach(b -> verify(b, times(1)).buildAndSend(any(), any()));

        // empty service name
        Whitebox.setInternalState(processor, "service", "");
        processor.process();
        builders.stream().forEach(b -> verify(b, times(0)).buildAndSend(any(), any()));
        Whitebox.setInternalState(processor, "service", "service");

        // empty builder list
        context = Mockito.spy(context);
        when(context.enabledBuilders()).thenReturn(Collections.emptyList());
        processor.process();
        builders.stream().forEach(b -> verify(b, times(0)).buildAndSend(any(), any()));
    }

    /**
     * Verify the basic data on {@link EvalData}
     */
    private <T> T verifyBaseData(EvalMultipleData data, String name, Map<String, String> labels) {
        final List<EvalData> dataList = (List<EvalData>) Whitebox.getInternalState(data, "dataList");
        Assert.assertEquals(1, dataList.size());

        final EvalData evalData = dataList.get(0);
        Assert.assertEquals(name, evalData.getName());
        Assert.assertEquals(labels, evalData.getLabels());
        Assert.assertEquals(processor, evalData.getProcessor());
        return (T) evalData;
    }

}
