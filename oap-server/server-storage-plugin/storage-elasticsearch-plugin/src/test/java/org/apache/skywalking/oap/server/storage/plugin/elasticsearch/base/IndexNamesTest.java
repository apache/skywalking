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

import java.util.Collections;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.ElasticSearchModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexNamesTest {

    private static final IndexFamily METRICS = new IndexFamily("metrics-apdex", IndexRolling.DAY_STEP);
    private static final IndexFamily SEGMENT = new IndexFamily("segment", IndexRolling.SUPER_DATASET_DAY_STEP);
    private static final IndexFamily MANAGEMENT = new IndexFamily("management", IndexRolling.NONE);

    @AfterEach
    void tearDown() {
        IndexNames.configure(1, -1);
    }

    @Test
    void configure_withoutAPositiveSuperDatasetDayStep_usesTheDayStepForSuperDatasets() {
        IndexNames.configure(3, -1);

        assertEquals("segment-20200807", IndexNames.pointIndex(SEGMENT, 20200809L, Step.DAY));
    }

    @Test
    void pointIndex_rollsTheDayIntoItsGroupCountedFrom2000() {
        IndexNames.configure(11, -1);

        assertEquals("metrics-apdex-20000101", IndexNames.pointIndex(METRICS, 20000105L, Step.DAY));
        assertEquals("metrics-apdex-20000101", IndexNames.pointIndex(METRICS, 20000111L, Step.DAY));
        assertEquals("metrics-apdex-20000112", IndexNames.pointIndex(METRICS, 20000112L, Step.DAY));
        assertEquals("metrics-apdex-20000112", IndexNames.pointIndex(METRICS, 20000122L, Step.DAY));
        assertEquals("metrics-apdex-20000123", IndexNames.pointIndex(METRICS, 20000123L, Step.DAY));
        assertEquals("metrics-apdex-20000123", IndexNames.pointIndex(METRICS, 20000125L, Step.DAY));
    }

    @Test
    void pointIndex_takesTheDayOfThePointAtTheStepPrecision() {
        assertEquals("metrics-apdex-20220710", IndexNames.pointIndex(METRICS, 20220710111111L, Step.SECOND));
        assertEquals("metrics-apdex-20220710", IndexNames.pointIndex(METRICS, 202207101111L, Step.MINUTE));
        assertEquals("metrics-apdex-20220710", IndexNames.pointIndex(METRICS, 2022071011L, Step.HOUR));
        assertEquals("metrics-apdex-20220710", IndexNames.pointIndex(METRICS, 20220710L, Step.DAY));
        assertEquals("segment-20220710", IndexNames.pointIndex(SEGMENT, 20220710111111L, Step.SECOND));
    }

    @Test
    void pointIndex_rejectsABlankTableOrAPointThatIsNotPositive() {
        assertThrows(
            IllegalArgumentException.class,
            () -> IndexNames.pointIndex(new IndexFamily(" ", IndexRolling.DAY_STEP), 20220710L, Step.DAY)
        );
        assertThrows(IllegalArgumentException.class, () -> IndexNames.pointIndex(METRICS, 0, Step.DAY));
    }

    @Test
    void writeIndex_rollsMetricsAndRecordsByDayStepAndSuperDatasetsBySuperDatasetDayStep() {
        IndexNames.configure(3, 1);
        final Model superDataset = model("superDatasetModel", DownSampling.Second, true, Record.class);
        final Model record = model("normalRecordModel", DownSampling.Second, false, Record.class);
        final Model metrics = model("normalMetricsModel", DownSampling.Minute, false, Metrics.class);

        assertEquals("superDatasetModel-20200809", IndexNames.writeIndex(superDataset, 2020_0809_1010_59L));
        assertEquals("records-all-20200807", IndexNames.writeIndex(record, 2020_0809_1010_59L));
        assertEquals("metrics-all-20200807", IndexNames.writeIndex(metrics, 2020_0809_1010L));
        assertEquals("superDatasetModel-20200810", IndexNames.writeIndex(superDataset, 2020_0810_1010_59L));
        assertEquals("records-all-20200810", IndexNames.writeIndex(record, 2020_0810_1010_59L));
        assertEquals("metrics-all-20200810", IndexNames.writeIndex(metrics, 2020_0810_1010L));
    }

    @Test
    void writeIndex_ofManagementData_isTheTableWithoutADate() {
        final Model management = model("ui_template", DownSampling.None, false, Record.class);

        assertEquals("management", IndexNames.writeIndex(management, 20200809L));
    }

    @Test
    void rangeIndices_listsEachRolledDayOnce() {
        assertArrayEquals(
            new String[] {"segment-20200601", "segment-20200602", "segment-20200603", "segment-20200604", "segment-20200605"},
            IndexNames.rangeIndices(SEGMENT, duration("2020-06-01 1400", "2020-06-05 1400"))
        );
        IndexNames.configure(1, 2);
        assertArrayEquals(
            new String[] {"segment-20200531", "segment-20200602", "segment-20200604"},
            IndexNames.rangeIndices(SEGMENT, duration("2020-06-01 1400", "2020-06-05 1400"))
        );
    }

    @Test
    void rangeIndices_withinOneDay_isThatDaysIndex() {
        assertArrayEquals(
            new String[] {"segment-20200605"},
            IndexNames.rangeIndices(SEGMENT, duration("2020-06-05 1400", "2020-06-05 1200"))
        );
    }

    @Test
    void rangeIndices_endingOnAnEarlierDayOrWithoutDuration_isTheAlias() {
        assertArrayEquals(
            new String[] {"segment"}, IndexNames.rangeIndices(SEGMENT, duration("2020-06-05 1400", "2020-06-01 1400")));
        assertArrayEquals(new String[] {"segment"}, IndexNames.rangeIndices(SEGMENT, null));
    }

    @Test
    void rangeIndicesBetweenTimestamps_listsTheDaysOfTheLocalTimestamps() {
        IndexNames.configure(1, 3);

        assertArrayEquals(
            new String[] {"segment-20200807", "segment-20200810"},
            IndexNames.rangeIndicesBetweenTimestamps(
                SEGMENT, new DateTime(2020, 8, 8, 12, 0).getMillis(), new DateTime(2020, 8, 10, 12, 0).getMillis())
        );
    }

    @Test
    void latestIndex_isTodaysIndex() {
        IndexNames.configure(3, 1);
        final long today = new DateTime(2020, 8, 9, 10, 10).getMillis();

        assertEquals("metrics-apdex-20200807", IndexNames.latestIndex(METRICS, today));
        assertEquals("segment-20200809", IndexNames.latestIndex(SEGMENT, today));
        assertThrows(IllegalArgumentException.class, () -> IndexNames.latestIndex(MANAGEMENT, today));
    }

    @Test
    void indexStartDay_isTheDateSuffix() {
        assertEquals(20190602L, IndexNames.indexStartDay("Index_Test-20190602"));
        assertEquals(20190602L, IndexNames.indexStartDay("namespace_metrics-all-20190602"));
    }

    private static Model model(final String name,
                               final DownSampling downSampling,
                               final boolean superDataset,
                               final Class<?> streamClass) {
        return new Model(
            name, Collections.emptyList(), 0, downSampling, superDataset, streamClass, true, false,
            new SQLDatabaseModelExtension(), new BanyanDBModelExtension(), new ElasticSearchModelExtension()
        );
    }

    private static Duration duration(final String start, final String end) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(start);
        duration.setEnd(end);
        return duration;
    }
}
