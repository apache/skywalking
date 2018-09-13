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

package org.apache.skywalking.oap.server.core.analysis.indicator;

import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class IndicatorTest {
    @Test
    public void testTransferToTimeBucket() {
        IndicatorMocker mocker = new IndicatorMocker();

        mocker.setTimeBucket(201809120511L);
        Assert.assertEquals(2018091205L, mocker.toTimeBucketInHour());
        Assert.assertEquals(20180912L, mocker.toTimeBucketInDay());
        Assert.assertEquals(201809L, mocker.toTimeBucketInMonth());

        mocker = new IndicatorMocker();

        mocker.setTimeBucket(2018091205L);
        Assert.assertEquals(20180912L, mocker.toTimeBucketInDay());
        Assert.assertEquals(201809L, mocker.toTimeBucketInMonth());

        mocker = new IndicatorMocker();

        mocker.setTimeBucket(20180912L);
        Assert.assertEquals(201809L, mocker.toTimeBucketInMonth());
    }

    @Test
    public void testIllegalTransferToTimeBucket() {
        IndicatorMocker mocker = new IndicatorMocker();
        mocker.setTimeBucket(2018091205L);

        boolean status = true;
        try {
            mocker.toTimeBucketInHour();
        } catch (IllegalStateException e) {
            status = false;
        }
        Assert.assertFalse(status);

        mocker = new IndicatorMocker();
        mocker.setTimeBucket(20180912L);

        status = true;
        try {
            mocker.toTimeBucketInHour();
        } catch (IllegalStateException e) {
            status = false;
        }
        Assert.assertFalse(status);

        status = true;
        try {
            mocker.toTimeBucketInDay();
        } catch (IllegalStateException e) {
            status = false;
        }
        Assert.assertFalse(status);
    }

    public class IndicatorMocker extends Indicator {

        @Override public String id() {
            return null;
        }

        @Override public void combine(Indicator indicator) {

        }

        @Override public void calculate() {

        }

        @Override public Indicator toHour() {
            return null;
        }

        @Override public Indicator toDay() {
            return null;
        }

        @Override public Indicator toMonth() {
            return null;
        }

        @Override public void deserialize(RemoteData remoteData) {

        }

        @Override public RemoteData.Builder serialize() {
            return null;
        }

        @Override public int remoteHashCode() {
            return 0;
        }
    }
}
