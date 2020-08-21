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
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.junit.Assert;
import org.junit.Test;

public class TimeSeriesUtilsTestCase {

    @Test
    public void indexTimeSeries() {
        Assert.assertEquals(20190602, TimeSeriesUtils.isolateTimeFromIndexName("Index_Test-20190602"));
    }

    @Test
    public void querySuperDatasetIndices() {
        String[] indices = TimeSeriesUtils.superDatasetIndexNames(SegmentRecord.INDEX_NAME, 20200601140000L, 20200605140000L);
        Assert.assertEquals(indices.length, 5);
        indices = TimeSeriesUtils.superDatasetIndexNames(SegmentRecord.INDEX_NAME, 20200605140000L, 20200605140000L);
        Assert.assertEquals(indices.length, 1);
        indices = TimeSeriesUtils.superDatasetIndexNames(SegmentRecord.INDEX_NAME, 20200605140000L, 20200601140000L);
        Assert.assertEquals(indices.length, 1);
        TimeSeriesUtils.setSUPER_DATASET_DAY_STEP(2);
        indices = TimeSeriesUtils.superDatasetIndexNames(SegmentRecord.INDEX_NAME, 20200601140000L, 20200605140000L);
        Assert.assertEquals(indices.length, 3);
        indices = TimeSeriesUtils.superDatasetIndexNames(SegmentRecord.INDEX_NAME, 20200605140000L, 20200605140000L);
        Assert.assertEquals(indices.length, 1);
        indices = TimeSeriesUtils.superDatasetIndexNames(SegmentRecord.INDEX_NAME, 20200605140000L, 20200601140000L);
        Assert.assertEquals(indices.length, 1);

    }
}
