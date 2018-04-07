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

package org.apache.skywalking.apm.collector.core.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class TimeBucketUtilsTest {

    @Test
    public void testGetMinuteTimeBucket() throws ParseException {
        SimpleDateFormat minuteDateFormat = new SimpleDateFormat("yyyyMMddHHmm");
        long timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(minuteDateFormat.parse("201803010201").getTime());
        Assert.assertEquals(201803010201L, timeBucket);
    }

    @Test
    public void testGetSecondTimeBucket() throws ParseException {
        SimpleDateFormat minuteDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        long timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(minuteDateFormat.parse("20180301020102").getTime());
        Assert.assertEquals(20180301020102L, timeBucket);
    }

    /**
     * Performance tests
     * Running with vm option: -javaagent: collector-instrument-agent.jar
     *
     * @param args
     */
    public static void main(String[] args) {
        long now = System.currentTimeMillis();

        for (int i = 0; i < 500000; i++) {
            TimeBucketUtils.INSTANCE.getMinuteTimeBucket(now);
        }
    }
}
