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

package org.apache.skywalking.oap.server.core.alarm.provider;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ThresholdTest {

    @Test
    public void setType() {
        Threshold threshold = new Threshold("my-rule", "75");
        threshold.setType(MetricsValueType.DOUBLE);
        assertEquals(0, Double.compare(75, threshold.getDoubleThreshold()));

        threshold.setType(MetricsValueType.INT);
        assertEquals(75, threshold.getIntThreshold());

        threshold.setType(MetricsValueType.LONG);
        assertEquals(75L, threshold.getLongThreshold());
    }

    @Test
    public void setTypeMultipleValues() {
        Threshold threshold = new Threshold("my-rule", "75,80, 90, -");
        threshold.setType(MetricsValueType.MULTI_INTS);
        assertArrayEquals(new Object[] {
            75,
            80,
            90,
            null
        }, threshold.getIntValuesThreshold());

    }

    @Test
    public void setTypeWithWrong() {
        Threshold threshold = new Threshold("my-rule", "wrong");
        threshold.setType(MetricsValueType.INT);
        assertEquals(0, threshold.getIntThreshold());
    }
}