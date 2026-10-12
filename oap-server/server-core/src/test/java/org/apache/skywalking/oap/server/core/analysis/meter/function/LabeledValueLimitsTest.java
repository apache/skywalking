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

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgLabeledFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.latest.LatestLabeledFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.max.MaxLabeledFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.sum.SumLabeledFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A labeled row keeps at most its metric's limit of label sets, through accept and through every merge.
 */
class LabeledValueLimitsTest {
    @BeforeAll
    static void setup() {
        MeterEntity.setNamingControl(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
        LabeledValueLimits.register(LimitedSum.class.getName(), 2);
        LabeledValueLimits.register(LimitedAvg.class.getName(), 2);
        LabeledValueLimits.register(LimitedMax.class.getName(), 2);
        LabeledValueLimits.register(LimitedLatest.class.getName(), 2);
    }

    @AfterAll
    static void tearDown() {
        MeterEntity.setNamingControl(null);
        LabeledValueLimits.register(LimitedSum.class.getName(), 0);
        LabeledValueLimits.register(LimitedAvg.class.getName(), 0);
        LabeledValueLimits.register(LimitedMax.class.getName(), 0);
        LabeledValueLimits.register(LimitedLatest.class.getName(), 0);
    }

    @Test
    void aSumKeepsTheLabelSetsItHasAndAddsUpToTheLimit() {
        final LimitedSum row = new LimitedSum();
        row.accept(entity(), table("{status=200},1|{status=404},1|{status=500},1"));
        assertEquals(2, row.getValue().size(), row.getValue().toString());

        final LimitedSum other = new LimitedSum();
        other.accept(entity(), table("{status=503},1"));
        final String kept = String.join(",", row.getValue().keys());
        row.combine(other);
        assertEquals(2, row.getValue().size(), "no new label set beyond the limit");
        assertEquals(kept, String.join(",", row.getValue().keys()));

        // A label set the row already has still adds up.
        final LimitedSum again = new LimitedSum();
        again.accept(entity(), table(row.getValue().keys().iterator().next() + ",5"));
        row.combine(again);
        assertEquals(2, row.getValue().size());
    }

    @Test
    void anAverageCountsOnlyTheLabelSetsItKept() {
        final LimitedAvg row = new LimitedAvg();
        row.accept(entity(), table("{a=1},10|{a=2},20"));
        final LimitedAvg other = new LimitedAvg();
        other.accept(entity(), table("{a=3},30"));
        row.combine(other);
        row.calculate();
        assertEquals(2, row.getValue().size(), row.getValue().toString());
    }

    @Test
    void aMaxAndALatestStopAtTheLimitToo() {
        final LimitedMax max = new LimitedMax();
        max.accept(entity(), table("{a=1},1|{a=2},2"));
        final LimitedMax maxOther = new LimitedMax();
        maxOther.accept(entity(), table("{a=3},3"));
        max.combine(maxOther);
        assertEquals(2, max.getValue().size());

        final LimitedLatest latest = new LimitedLatest();
        latest.accept(entity(), table("{a=1},1|{a=2},2"));
        final LimitedLatest latestOther = new LimitedLatest();
        latestOther.accept(entity(), table("{a=2},9|{a=3},3"));
        latest.combine(latestOther);
        assertEquals(2, latest.getValue().size());
        assertEquals(9L, latest.getValue().get("{a=2}"), "a kept label set still takes the latest value");
    }

    @Test
    void aMetricWithoutALimitKeepsEverything() {
        final UnlimitedSum row = new UnlimitedSum();
        row.accept(entity(), table("{a=1},1|{a=2},1|{a=3},1"));
        assertEquals(3, row.getValue().size());
    }

    private static MeterEntity entity() {
        return MeterEntity.newService("service-test", Layer.GENERAL);
    }

    private static DataTable table(final String data) {
        return new DataTable(data);
    }

    private static class LimitedSum extends SumLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new LimitedSum();
        }
    }

    private static class UnlimitedSum extends SumLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new UnlimitedSum();
        }
    }

    private static class LimitedAvg extends AvgLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new LimitedAvg();
        }
    }

    private static class LimitedMax extends MaxLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new LimitedMax();
        }
    }

    private static class LimitedLatest extends LatestLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new LimitedLatest();
        }
    }
}
