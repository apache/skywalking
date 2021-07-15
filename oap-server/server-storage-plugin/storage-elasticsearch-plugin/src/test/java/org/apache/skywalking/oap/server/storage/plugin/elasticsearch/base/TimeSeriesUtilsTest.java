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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.Lists;

import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils.compressTimeBucket;
import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils.writeIndexName;

public class TimeSeriesUtilsTest {

    private Model superDatasetModel;
    private Model normalRecordModel;
    private Model normalMetricsModel;

    @Before
    public void prepare() {
        superDatasetModel = new Model("superDatasetModel", Lists.newArrayList(), Lists.newArrayList(),
                                      0, DownSampling.Minute, true, true, "", true
        );
        normalRecordModel = new Model("normalRecordModel", Lists.newArrayList(), Lists.newArrayList(),
                                      0, DownSampling.Minute, true, false, "", true
        );
        normalMetricsModel = new Model("normalMetricsModel", Lists.newArrayList(), Lists.newArrayList(),
                                       0, DownSampling.Minute, false, false, "", true
        );
        TimeSeriesUtils.setSUPER_DATASET_DAY_STEP(1);
        TimeSeriesUtils.setDAY_STEP(3);
    }

    @Test
    public void testCompressTimeBucket() {
        Assert.assertEquals(20000101L, compressTimeBucket(20000105, 11));
        Assert.assertEquals(20000101L, compressTimeBucket(20000111, 11));
        Assert.assertEquals(20000112L, compressTimeBucket(20000112, 11));
        Assert.assertEquals(20000112L, compressTimeBucket(20000122, 11));
        Assert.assertEquals(20000123L, compressTimeBucket(20000123, 11));
        Assert.assertEquals(20000123L, compressTimeBucket(20000125, 11));
    }

    @Test
    public void testIndexRolling() {
        long secondTimeBucket = 2020_0809_1010_59L;
        long minuteTimeBucket = 2020_0809_1010L;

        Assert.assertEquals(
            "superDatasetModel-20200809",
            writeIndexName(superDatasetModel, secondTimeBucket)
        );
        Assert.assertEquals(
            "normalRecordModel-20200807",
            writeIndexName(normalRecordModel, secondTimeBucket)
        );
        Assert.assertEquals(
            "normalMetricsModel-20200807",
            writeIndexName(normalMetricsModel, minuteTimeBucket)
        );
        secondTimeBucket += 1000000;
        minuteTimeBucket += 10000;
        Assert.assertEquals(
            "superDatasetModel-20200810",
            writeIndexName(superDatasetModel, secondTimeBucket)
        );
        Assert.assertEquals(
            "normalRecordModel-20200810",
            writeIndexName(normalRecordModel, secondTimeBucket)
        );
        Assert.assertEquals(
            "normalMetricsModel-20200810",
            writeIndexName(normalMetricsModel, minuteTimeBucket)
        );
    }

}
