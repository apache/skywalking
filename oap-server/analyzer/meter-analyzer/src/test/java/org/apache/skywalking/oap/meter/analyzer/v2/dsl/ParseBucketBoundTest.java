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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ParseBucketBoundTest {

    @ParameterizedTest
    @CsvSource({
        "+Inf, Infinity",
        "Inf, Infinity",
        "Infinity, Infinity",
        "+inf, Infinity",
        "INFINITY, Infinity",
        "-Inf, -Infinity",
        "-Infinity, -Infinity",
        "-inf, -Infinity",
    })
    void acceptsPrometheusAndJavaInfForms(final String raw, final String expected) {
        final double actual = SampleFamily.parseBucketBound(raw);
        assertEquals(Double.parseDouble(expected), actual);
    }

    @Test
    void acceptsFiniteBounds() {
        assertEquals(0.025d, SampleFamily.parseBucketBound("0.025"));
        assertEquals(1250d, SampleFamily.parseBucketBound("1250"));
    }

    @Test
    void rejectsNullGarbageAndNaN() {
        assertThrows(NumberFormatException.class, () -> SampleFamily.parseBucketBound(null));
        assertThrows(NumberFormatException.class, () -> SampleFamily.parseBucketBound("not-a-number"));
        assertThrows(NumberFormatException.class, () -> SampleFamily.parseBucketBound("NaN"));
        assertThrows(NumberFormatException.class, () -> SampleFamily.parseBucketBound("nan"));
    }
}
