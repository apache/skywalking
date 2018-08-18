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

package org.apache.skywalking.apm.collector.storage.table.global;

import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;

/**
 * @author peng-yongsheng
 */
public class GlobalTrace extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(GlobalTraceTable.ID, new NonMergeOperation()),
        new StringColumn(GlobalTraceTable.SEGMENT_ID, new CoverMergeOperation()),
        new StringColumn(GlobalTraceTable.TRACE_ID, new CoverMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(GlobalTraceTable.TIME_BUCKET, new CoverMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public GlobalTrace() {
        super(STRING_COLUMNS, LONG_COLUMNS, INTEGER_COLUMNS, DOUBLE_COLUMNS);
    }

    @Override public String getId() {
        return getDataString(0);
    }

    @Override public void setId(String id) {
        setDataString(0, id);
    }

    @Override public String getMetricId() {
        return getId();
    }

    @Override public void setMetricId(String metricId) {
        setId(metricId);
    }

    public String getSegmentId() {
        return getDataString(1);
    }

    public void setSegmentId(String segmentId) {
        setDataString(1, segmentId);
    }

    public String getTraceId() {
        return getDataString(2);
    }

    public void setTraceId(String globalTraceId) {
        setDataString(2, globalTraceId);
    }

    public Long getTimeBucket() {
        return getDataLong(0);
    }

    public void setTimeBucket(long timeBucket) {
        setDataLong(0, timeBucket);
    }
}
