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
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EvalMultipleDataTest extends EvalDataBaseTest {

    private EvalMultipleData singleMultiple;
    private EvalMultipleData histogramMultiple;

    @Before
    public void setup() {
        singleMultiple = new EvalMultipleData("test_counter", Arrays.asList(
            buildSingle("test_counter", new String[] {"k1", "v1"}, 10d),
            buildSingle("test_counter", new String[] {"k1", "v2"}, 20d),
            buildSingle("test_counter", new String[] {"k1", "v3"}, 30d)
        ));
        histogramMultiple = new EvalMultipleData("test_histogram", Arrays.asList(
            buildHistogram("test_histogram", new String[] {"k1", "v1"}, 1, 10, 5, 20, 10, 3),
            buildHistogram("test_histogram", new String[] {"k1", "v2"}, 1, 5, 5, 10, 10, 7),
            buildHistogram("test_histogram", new String[] {"k1", "v3"}, 1, 15, 5, 20, 10, 25)
        ));
    }

    @Test
    public void testTagFilter() {
        final EvalMultipleData filedData = singleMultiple.tagFilter("k1", "v1");
        final List<EvalData> dataList = Whitebox.getInternalState(filedData, "dataList");
        Assert.assertEquals(1, dataList.size());

        EvalSingleData singleData = (EvalSingleData) dataList.get(0);
        Assert.assertEquals("test_counter", singleData.getName());
        Assert.assertEquals("v1", singleData.getLabels().get("k1"));
        Assert.assertEquals(10d, singleData.getValue(), 0.0);
    }

    @Test
    public void testOperation() {
        // Add
        assertSingleOperation(singleMultiple, EvalMultipleData::add, 10, 20, 30, 40);
        assertSingleOperation(singleMultiple, EvalMultipleData::add, singleMultiple.tagFilter("k1", "v1"), 20, 30, 40);
        assertSingleOperation(singleMultiple, EvalMultipleData::add, singleMultiple.tagFilter("unknown", "unknown"), 10, 20 , 30);
        assertSingleOperationThrowArgs(singleMultiple, EvalMultipleData::add, singleMultiple);

        // Reduce
        assertSingleOperation(singleMultiple, EvalMultipleData::minus, 10, 0, 10, 20);
        assertSingleOperation(singleMultiple, EvalMultipleData::minus, singleMultiple.tagFilter("k1", "v1"), 0, 10, 20);
        assertSingleOperation(singleMultiple, EvalMultipleData::minus, singleMultiple.tagFilter("unknown", "unknown"), 10, 20 , 30);
        assertSingleOperationThrowArgs(singleMultiple, EvalMultipleData::minus, singleMultiple);

        // Multiply
        assertSingleOperation(singleMultiple, EvalMultipleData::multiply, 10, 100, 200, 300);
        assertSingleOperation(singleMultiple, EvalMultipleData::multiply, singleMultiple.tagFilter("k1", "v1"), 100, 200, 300);
        assertSingleOperation(singleMultiple, EvalMultipleData::multiply, singleMultiple.tagFilter("unknown", "unknown"), 10, 20 , 30);
        assertSingleOperationThrowArgs(singleMultiple, EvalMultipleData::multiply, singleMultiple);

        // Mean
        assertSingleOperation(singleMultiple, EvalMultipleData::divide, 10, 1, 2, 3);
        assertSingleOperation(singleMultiple, EvalMultipleData::divide, singleMultiple.tagFilter("k1", "v1"), 1, 2, 3);
        assertSingleOperation(singleMultiple, EvalMultipleData::divide, singleMultiple.tagFilter("unknown", "unknown"), 10, 20 , 30);
        assertSingleOperationThrowArgs(singleMultiple, EvalMultipleData::divide, singleMultiple);

        // Irate
        assertSingleOperation(singleMultiple, EvalMultipleData::irate, "PT15S", 0d, 0d, 0d);

        // Rate
        assertSingleOperation(singleMultiple, EvalMultipleData::rate, "PT15S", 0d, 0d, 0d);

        // Increase
        assertSingleOperation(singleMultiple, EvalMultipleData::increase, "PT15S", 0, 0, 0);
    }

    @Test
    public void testCombineAsSingleValue() {
        final EvalSingleData combinedSingleData = (EvalSingleData) singleMultiple.combineAsSingleData();
        Assert.assertEquals(60d, combinedSingleData.getValue(), 0.0);

        final EvalHistogramData combinedHistogramData = (EvalHistogramData) histogramMultiple.combineAsSingleData();
        Assert.assertEquals(30, combinedHistogramData.getBuckets().get(1d).longValue());
        Assert.assertEquals(50, combinedHistogramData.getBuckets().get(5d).longValue());
        Assert.assertEquals(35, combinedHistogramData.getBuckets().get(10d).longValue());
    }

    @Test
    public void testCombineAndGroupBy() {
        final Map<String, EvalData> singleValueCombineAndGroupBy = singleMultiple.combineAndGroupBy(Arrays.asList("k1"));
        Assert.assertEquals(3, singleValueCombineAndGroupBy.size());
        Assert.assertEquals(10d, ((EvalSingleData) singleValueCombineAndGroupBy.get("v1")).getValue(), 0.0);
        Assert.assertEquals(20d, ((EvalSingleData) singleValueCombineAndGroupBy.get("v2")).getValue(), 0.0);
        Assert.assertEquals(30d, ((EvalSingleData) singleValueCombineAndGroupBy.get("v3")).getValue(), 0.0);

        final Map<String, EvalData> histogramCombineAndGroupBy = histogramMultiple.combineAndGroupBy(Arrays.asList("k1"));
        Assert.assertEquals(3, histogramCombineAndGroupBy.size());
        Assert.assertEquals(10, ((EvalHistogramData) histogramCombineAndGroupBy.get("v1")).getBuckets().get(1d).longValue());
        Assert.assertEquals(20, ((EvalHistogramData) histogramCombineAndGroupBy.get("v1")).getBuckets().get(5d).longValue());
        Assert.assertEquals(3, ((EvalHistogramData) histogramCombineAndGroupBy.get("v1")).getBuckets().get(10d).longValue());

        Assert.assertEquals(5, ((EvalHistogramData) histogramCombineAndGroupBy.get("v2")).getBuckets().get(1d).longValue());
        Assert.assertEquals(10, ((EvalHistogramData) histogramCombineAndGroupBy.get("v2")).getBuckets().get(5d).longValue());
        Assert.assertEquals(7, ((EvalHistogramData) histogramCombineAndGroupBy.get("v2")).getBuckets().get(10d).longValue());

        Assert.assertEquals(15, ((EvalHistogramData) histogramCombineAndGroupBy.get("v3")).getBuckets().get(1d).longValue());
        Assert.assertEquals(20, ((EvalHistogramData) histogramCombineAndGroupBy.get("v3")).getBuckets().get(5d).longValue());
        Assert.assertEquals(25, ((EvalHistogramData) histogramCombineAndGroupBy.get("v3")).getBuckets().get(10d).longValue());

    }

    /**
     * Verify the multiple data operation
     */
    private <T> void assertSingleOperation(EvalMultipleData data, Function2<EvalMultipleData, T, EvalMultipleData> operation, T value, double... results) {
        final EvalMultipleData evalData = operation.apply(data, value);
        final List<EvalData> dataList = Whitebox.getInternalState(evalData, "dataList");
        Assert.assertEquals(results.length, dataList.size());
        for (int i = 0; i < results.length; i++) {
            final EvalSingleData perSingleData = (EvalSingleData) dataList.get(i);
            Assert.assertEquals(data.getName(), perSingleData.getName());
            Assert.assertEquals(results[i], perSingleData.getValue(), 0.0);
        }
    }

    private <T> void assertSingleOperationThrowArgs(EvalMultipleData data, Function2<EvalMultipleData, T, EvalMultipleData> operation, T value) {
        try {
            operation.apply(data, value);
            throw new IllegalStateException("Method is not matches");
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            throw e;
        }
    }
}
