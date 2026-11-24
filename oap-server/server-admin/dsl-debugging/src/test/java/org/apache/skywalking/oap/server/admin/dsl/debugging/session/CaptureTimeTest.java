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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CaptureTimeTest {

    @Test
    void itReadsAsMillisDotSixDigitNanos() {
        assertEquals("1795505219786.871986", new CaptureTime(1795505219786L, 871986).toString());
        assertEquals("1795505219786.000500", new CaptureTime(1795505219786L, 500).toString());
        assertEquals(new CaptureTime(1795505219786L, 500), CaptureTime.parse("1795505219786.000500"));
    }

    @Test
    void millisCompareFirstThenNanos() {
        final CaptureTime early = new CaptureTime(10L, 999_999);
        final CaptureTime late = new CaptureTime(11L, 0);
        assertTrue(early.compareTo(late) < 0);
        assertTrue(new CaptureTime(10L, 5).compareTo(new CaptureTime(10L, 7)) < 0);
        assertEquals(0, new CaptureTime(10L, 5).compareTo(new CaptureTime(10L, 5)));
        assertTrue(CaptureTime.EARLIEST.compareTo(new CaptureTime(0L, 0)) < 0);
    }

    @Test
    void anythingElseIsNoCaptureTime() {
        for (final String text : new String[] {"1795505219786", "1795505219786.8719", ".000001", "-1.000000",
            "abc.000001", "1795505219786.abcdef", "1795505219786.-00001", "1795505219786.+00001",
            "+1795505219786.000000", "\uFF11\uFF12\uFF13.000001", "99999999999999999999.000000"}) {
            assertThrows(IllegalArgumentException.class, () -> CaptureTime.parse(text), text);
        }
    }

    @Test
    void nanosStayWithinTheirMillisecond() {
        assertThrows(IllegalArgumentException.class, () -> new CaptureTime(10L, -1));
        assertThrows(IllegalArgumentException.class, () -> new CaptureTime(10L, 1_000_000));
    }
}
