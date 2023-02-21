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

import com.google.common.collect.Lists;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.ElasticSearchModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils.compressTimeBucket;
import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils.writeIndexName;

public class TimeSeriesUtilsTest {

    private Model superDatasetModel;
    private Model normalRecordModel;
    private Model normalMetricsModel;

    @BeforeEach
    public void prepare() {
        superDatasetModel = new Model("superDatasetModel", Lists.newArrayList(),
                                      0, DownSampling.Second, true, Record.class, true,
                                      new SQLDatabaseModelExtension(), new BanyanDBModelExtension(), new ElasticSearchModelExtension()
        );
        normalRecordModel = new Model("normalRecordModel", Lists.newArrayList(),
                                      0, DownSampling.Second, true, Record.class, true,
                                      new SQLDatabaseModelExtension(), new BanyanDBModelExtension(), new ElasticSearchModelExtension()
        );
        normalMetricsModel = new Model("normalMetricsModel", Lists.newArrayList(),
                                       0, DownSampling.Minute, false, Metrics.class, true,
                                       new SQLDatabaseModelExtension(), new BanyanDBModelExtension(), new ElasticSearchModelExtension()
        );
        TimeSeriesUtils.setSUPER_DATASET_DAY_STEP(1);
        TimeSeriesUtils.setDAY_STEP(3);
    }

    @Test
    public void testCompressTimeBucket() {
        Assertions.assertEquals(20000101L, compressTimeBucket(20000105, 11));
        Assertions.assertEquals(20000101L, compressTimeBucket(20000111, 11));
        Assertions.assertEquals(20000112L, compressTimeBucket(20000112, 11));
        Assertions.assertEquals(20000112L, compressTimeBucket(20000122, 11));
        Assertions.assertEquals(20000123L, compressTimeBucket(20000123, 11));
        Assertions.assertEquals(20000123L, compressTimeBucket(20000125, 11));
    }

    @Test
    public void testIndexRolling() {
        long secondTimeBucket = 2020_0809_1010_59L;
        long minuteTimeBucket = 2020_0809_1010L;

        Assertions.assertEquals(
            "superDatasetModel-20200809",
            writeIndexName(superDatasetModel, secondTimeBucket)
        );
        Assertions.assertEquals(
            "records-all-20200807",
            writeIndexName(normalRecordModel, secondTimeBucket)
        );
        Assertions.assertEquals(
            "metrics-all-20200807",
            writeIndexName(normalMetricsModel, minuteTimeBucket)
        );
        secondTimeBucket += 1000000;
        minuteTimeBucket += 10000;
        Assertions.assertEquals(
            "superDatasetModel-20200810",
            writeIndexName(superDatasetModel, secondTimeBucket)
        );
        Assertions.assertEquals(
            "records-all-20200810",
            writeIndexName(normalRecordModel, secondTimeBucket)
        );
        Assertions.assertEquals(
            "metrics-all-20200810",
            writeIndexName(normalMetricsModel, minuteTimeBucket)
        );
    }

    @Test
    public void queryIndexNameTest() {
        Assertions.assertEquals(
            "metrics-apdex-20220710",
            TimeSeriesUtils.queryIndexName("metrics-apdex", 20220710111111L, Step.SECOND, false, false)
        );
        Assertions.assertEquals(
            "metrics-apdex-20220710",
            TimeSeriesUtils.queryIndexName("metrics-apdex", 202207101111L, Step.MINUTE, false, false)
        );
        Assertions.assertEquals(
            "metrics-apdex-20220710",
            TimeSeriesUtils.queryIndexName("metrics-apdex", 2022071011L, Step.HOUR, false, false)
        );
        Assertions.assertEquals(
            "metrics-apdex-20220710",
            TimeSeriesUtils.queryIndexName("metrics-apdex", 20220710L, Step.DAY, false, false)
        );
        Assertions.assertEquals(
            "metrics-apdex-20220710",
            TimeSeriesUtils.queryIndexName("metrics-apdex", 20220710111111L, Step.DAY, true, true)
        );
    }

}
