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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class EvalSingleDataTest extends EvalDataBaseTest {

    private EvalSingleData singleData;
    private EvalSingleData operateSingleData;
    private EvalHistogramData operateHistogramData;

    @Before
    public void setup() {
        singleData = buildSingle("test_counter", new String[] {"k1", "v1"}, 10d);
        operateSingleData = buildSingle("test_counter1", new String[] {"k1", "v1"}, 5d);
        operateHistogramData = buildHistogram("test_histogram", new String[] {"k1", "v1"}, 1, 10);
    }

    @Test
    public void testOperation() {
        // Add
        assertSingleOperation(EvalSingleData::add, 5d, 15d);
        assertSingleOperation(EvalSingleData::add, operateSingleData, 15d);
        assertSingleOperationThrowIllArgs(EvalSingleData::add, operateHistogramData);

        // Reduce
        assertSingleOperation(EvalSingleData::minus, 5d, 5d);
        assertSingleOperation(EvalSingleData::minus, operateSingleData, 5d);
        assertSingleOperationThrowIllArgs(EvalSingleData::minus, operateHistogramData);

        // Multiply
        assertSingleOperation(EvalSingleData::multiply, 5d, 50d);
        assertSingleOperation(EvalSingleData::multiply, operateSingleData, 50d);
        assertSingleOperationThrowIllArgs(EvalSingleData::multiply, operateHistogramData);

        // Mean
        assertSingleOperation(EvalSingleData::divide, 5d, 2d);
        assertSingleOperation(EvalSingleData::divide, operateSingleData, 2d);
        assertSingleOperationThrowIllArgs(EvalSingleData::divide, operateHistogramData);

        // Scale
        assertSingleOperation(EvalSingleData::scale, 2, 1000d);

        // Irate
        assertSingleOperation(EvalSingleData::irate, "PT15S", 0.0);

        // Rate
        assertSingleOperation(EvalSingleData::rate, "PT15S", 0.0);

        // Increase
        assertSingleOperation(EvalSingleData::increase, "PT15S", 0d);
    }

    @Test
    public void testCombine() {
        assertSingleOperation(EvalSingleData::combine, operateSingleData, 15d);
    }

    private <T> void assertSingleOperation(Function2<EvalSingleData, T, EvalData> operation, T value, double result) {
        final EvalData evalData = operation.apply(singleData, value);
        Assert.assertTrue(evalData instanceof EvalSingleData);
        Assert.assertEquals(singleData.getName(), evalData.getName());
        Assert.assertEquals(singleData.getLabels(), evalData.getLabels());
        Assert.assertEquals(singleData.getProcessor(), evalData.getProcessor());
        Assert.assertEquals(result, ((EvalSingleData) evalData).getValue(), 0.0);
    }

    private void assertSingleOperationThrowIllArgs(Function2<EvalSingleData, EvalData, EvalData> operation, EvalData data) {
        try {
            operation.apply(singleData, data);
            throw new IllegalStateException("Operation not matches with data:" + data);
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            throw e;
        }
    }
}
