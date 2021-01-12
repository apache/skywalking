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

import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramPercentileFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
public class MeterBuilderTest extends MeterBaseTest {

    @Test
    public void testBuildAndSend() throws ModuleStartException {
        List<AcceptableValue> values = new ArrayList<>();
        doAnswer(invocationOnMock -> {
            values.add(invocationOnMock.getArgument(0, AcceptableValue.class));
            return null;
        }).when(meterSystem).doStreamingCalculation(any());

        // Prcess the meters
        processor.process();

        Assert.assertEquals(3, values.size());
        // Check avg
        final AvgFunction avg = (AvgFunction) values.get(0);
        Assert.assertEquals(1, avg.getSummation());
        Assert.assertEquals(1, avg.getCount());
        Assert.assertEquals(IDManager.ServiceID.buildId("service", true), avg.getServiceId());
        Assert.assertEquals(IDManager.ServiceID.buildId("service", true), avg.getEntityId());
        Assert.assertEquals(TimeBucket.getMinuteTimeBucket(timestamp), avg.getTimeBucket());

        // Check avgHistogram
        final AvgHistogramFunction avgHistogram = (AvgHistogramFunction) values.get(1);
        verifyDataTable(avgHistogram.getSummation(), 1, 10, 5, 15, 10, 3);
        verifyDataTable(avgHistogram.getCount(), 1, 1, 5, 1, 10, 1);
        Assert.assertEquals(IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("service", true), "instance"), avgHistogram.getEntityId());
        Assert.assertEquals(TimeBucket.getMinuteTimeBucket(timestamp), avgHistogram.getTimeBucket());

        // Check avgHistogramPercentile
        final AvgHistogramPercentileFunction avgPercentile = (AvgHistogramPercentileFunction) values.get(2);
        Assert.assertEquals(3, avgPercentile.getRanks().size());
        Assert.assertEquals(50, avgPercentile.getRanks().get(0));
        Assert.assertEquals(90, avgPercentile.getRanks().get(1));
        Assert.assertEquals(99, avgPercentile.getRanks().get(2));
        Assert.assertEquals(IDManager.EndpointID.buildId(
            IDManager.ServiceID.buildId("service", true), "test_endpoint"), avgPercentile.getEntityId());
        verifyDataTable(avgPercentile.getSummation(), 1, 10, 5, 15, 10, 3);
        verifyDataTable(avgPercentile.getCount(), 1, 1, 5, 1, 10, 1);
    }

    private void verifyDataTable(DataTable table, Object... data) {
        Assert.assertEquals(data.length / 2, table.size());
        for (int i = 0; i < data.length; i += 2) {
            Assert.assertEquals(
                Long.parseLong(String.valueOf(data[i + 1])), table.get(String.valueOf(data[i])).longValue());
        }
    }
}
