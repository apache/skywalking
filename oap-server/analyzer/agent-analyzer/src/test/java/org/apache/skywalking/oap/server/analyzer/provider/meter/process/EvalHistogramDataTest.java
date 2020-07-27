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

import io.vavr.Function2;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

public class EvalHistogramDataTest {

    private EvalHistogramData histogramData;
    private EvalHistogramData operateHistogramData;

    @Before
    public void setup() {
        final MeterProcessor processor = Mockito.spy(new MeterProcessor(null));
        when(processor.window()).thenReturn(Window.getWindow("service", "instance"));
        histogramData = EvalHistogramData.build(
            MeterHistogram.newBuilder()
                .setName("test_histogram1")
                .addLabels(Label.newBuilder().setName("k1").setValue("v1").build())
                .addValues(MeterBucketValue.newBuilder().setBucket(1).setCount(10).build())
                .addValues(MeterBucketValue.newBuilder().setBucket(10).setCount(15).build())
                .addValues(MeterBucketValue.newBuilder().setBucket(20).setCount(3).build())
            .build(),
            processor
        );

        operateHistogramData = EvalHistogramData.build(
            MeterHistogram.newBuilder()
                .setName("test_histogram2")
                .addLabels(Label.newBuilder().setName("k1").setValue("v1").build())
                .addValues(MeterBucketValue.newBuilder().setBucket(1).setCount(10).build())
                .addValues(MeterBucketValue.newBuilder().setBucket(10).setCount(15).build())
                .addValues(MeterBucketValue.newBuilder().setBucket(20).setCount(3).build())
            .build(),
            processor
        );
    }

    @Test
    public void testOperation() {
        // Add
        assertHistogramOperationUnSupport(EvalHistogramData::add, 5d);
        assertHistogramOperationUnSupport(EvalHistogramData::add, operateHistogramData);

        // Reduce
        assertHistogramOperationUnSupport(EvalHistogramData::minus, 5d);
        assertHistogramOperationUnSupport(EvalHistogramData::minus, operateHistogramData);

        // Multiply
        assertHistogramOperationUnSupport(EvalHistogramData::multiply, 5d);
        assertHistogramOperationUnSupport(EvalHistogramData::multiply, operateHistogramData);

        // Mean
        assertHistogramOperationUnSupport(EvalHistogramData::divide, 5d);
        assertHistogramOperationUnSupport(EvalHistogramData::divide, operateHistogramData);

        // Scale
        assertHistogramOperationUnSupport(EvalHistogramData::scale, 2);

        // Irate
        assertHistogramOperation(EvalHistogramData::irate, "PT15S", 1, 0d, 10, 0d, 20, 0d);

        // Rate
        assertHistogramOperation(EvalHistogramData::rate, "PT15S", 1, 0d, 10, 0d, 20, 0d);

        // Increase
        assertHistogramOperation(EvalHistogramData::increase, "PT15S", 1, 0d, 10, 0d, 20, 0d);
    }

    @Test
    public void testCombine() {
        assertHistogramOperation(EvalHistogramData::combine, operateHistogramData, 1, 20, 10, 30, 20, 6);
    }

    /**
     * Check histogram operation and values
     */
    private <T> void assertHistogramOperation(Function2<EvalHistogramData, T, EvalData> operation, T value, double... data) {
        final EvalData evalData = operation.apply(histogramData, value);
        Assert.assertTrue(evalData instanceof EvalHistogramData);
        Assert.assertEquals(histogramData.getName(), evalData.getName());
        Assert.assertEquals(histogramData.getLabels(), evalData.getLabels());
        Assert.assertEquals(histogramData.getProcessor(), evalData.getProcessor());
        final EvalHistogramData histogramData = (EvalHistogramData) evalData;
        Assert.assertEquals(data.length / 2, histogramData.getBuckets().size());
        for (int i = 0; i < data.length; i += 2) {
            Assert.assertEquals(data[i + 1], histogramData.getBuckets().get(data[i]).doubleValue(), 0.0);
        }
    }

    /**
     * Check histogram operation method not support
     */
    private <T> void assertHistogramOperationUnSupport(Function2<EvalHistogramData, T, EvalData> operation, T data) {
        try {
            operation.apply(histogramData, data);
            throw new IllegalStateException("Operation not matches with data:" + data);
        } catch (UnsupportedOperationException e) {
        } catch (Exception e) {
            throw e;
        }
    }

}
