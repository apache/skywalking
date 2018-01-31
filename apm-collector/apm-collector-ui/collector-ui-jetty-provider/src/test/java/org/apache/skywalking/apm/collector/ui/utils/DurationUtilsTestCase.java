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

package org.apache.skywalking.apm.collector.ui.utils;

import java.text.ParseException;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class DurationUtilsTestCase {

    @Test
    public void test() throws ParseException {
    }

    @Test
    public void testGetDurationPoints() throws ParseException {
        Long[] durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.MONTH, 201710, 201803);
        Assert.assertArrayEquals(new Long[] {201710L, 201711L, 201712L, 201801L, 201802L, 201803L}, durationPoints);

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.DAY, 20180129, 20180202);
        Assert.assertArrayEquals(new Long[] {20180129L, 20180130L, 20180131L, 20180201L, 20180202L}, durationPoints);

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.HOUR, 2018012922, 2018013002);
        Assert.assertArrayEquals(new Long[] {2018012922L, 2018012923L, 2018013000L, 2018013001L, 2018013002L}, durationPoints);

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.MINUTE, 201801292258L, 201801292302L);
        Assert.assertArrayEquals(new Long[] {201801292258L, 201801292259L, 201801292300L, 201801292301L, 201801292302L}, durationPoints);

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.SECOND, 20180129225858L, 20180129225902L);
        Assert.assertArrayEquals(new Long[] {20180129225858L, 20180129225859L, 20180129225900L, 20180129225901L, 20180129225902L}, durationPoints);
    }

    @Test(expected = UnexpectedException.class)
    public void testGetDurationPointsErrorDuration() throws ParseException {
        DurationUtils.INSTANCE.getDurationPoints(Step.MONTH, 20171001, 20180301);
    }
}
