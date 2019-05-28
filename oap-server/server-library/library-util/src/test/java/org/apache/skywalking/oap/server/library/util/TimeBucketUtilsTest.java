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

package org.apache.skywalking.oap.server.library.util;

import static org.junit.Assert.assertEquals;

import java.text.ParseException;

import org.joda.time.LocalDateTime;
import org.junit.Test;

/**
 * Unit tests for class {@link TimeBucketUtils}.
 *
 * @see TimeBucketUtils
 */
public class TimeBucketUtilsTest {


    @Test
    public void testFormatMinuteTimeBucket() throws ParseException {
        assertEquals("1970-01-01 00:00", TimeBucketUtils.INSTANCE.formatMinuteTimeBucket(197001010000L));
    }


    @Test
    public void testGetTime() {
        LocalDateTime localDateTime = new LocalDateTime(1L);

        assertEquals(19700101010000L, TimeBucketUtils.INSTANCE.getTime(localDateTime));
        assertEquals(4, localDateTime.size());
    }


    @Test
    public void testMinuteToDay() {
        assertEquals(0L, TimeBucketUtils.INSTANCE.minuteToDay(0L));
    }


    @Test
    public void testGetMinuteTimeBucket() {
        assertEquals(197001010100L, TimeBucketUtils.INSTANCE.getMinuteTimeBucket(10000));
    }


    @Test
    public void testMinuteToMonth() {
        assertEquals(0L, TimeBucketUtils.INSTANCE.minuteToMonth(0L));
    }


    @Test
    public void testMinuteToHour() {
        assertEquals((-45L), TimeBucketUtils.INSTANCE.minuteToHour((-4502L)));
    }


    @Test
    public void testGetSecondTimeBucket() {
        assertEquals(19700101005958L, TimeBucketUtils.INSTANCE.getSecondTimeBucket((-1506L)));
    }


}
