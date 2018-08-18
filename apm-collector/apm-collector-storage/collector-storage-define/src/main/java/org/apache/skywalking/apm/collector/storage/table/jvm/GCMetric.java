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

package org.apache.skywalking.apm.collector.storage.table.jvm;

import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;

/**
 * @author peng-yongsheng
 */
public class GCMetric extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(GCMetricTable.ID, new NonMergeOperation()),
        new StringColumn(GCMetricTable.METRIC_ID, new NonMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(GCMetricTable.COUNT, new AddMergeOperation()),
        new LongColumn(GCMetricTable.TIMES, new AddMergeOperation()),
        new LongColumn(GCMetricTable.TIME_BUCKET, new CoverMergeOperation()),
        new LongColumn(GCMetricTable.DURATION, new AddMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(GCMetricTable.INSTANCE_ID, new CoverMergeOperation()),
        new IntegerColumn(GCMetricTable.PHRASE, new CoverMergeOperation()),
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public GCMetric() {
        super(STRING_COLUMNS, LONG_COLUMNS, INTEGER_COLUMNS, DOUBLE_COLUMNS);
    }

    @Override public String getId() {
        return getDataString(0);
    }

    @Override public void setId(String id) {
        setDataString(0, id);
    }

    @Override public String getMetricId() {
        return getDataString(1);
    }

    @Override public void setMetricId(String metricId) {
        setDataString(1, metricId);
    }

    public Long getCount() {
        return getDataLong(0);
    }

    public void setCount(Long count) {
        setDataLong(0, count);
    }

    public Long getTimes() {
        return getDataLong(1);
    }

    public void setTimes(Long times) {
        setDataLong(1, times);
    }

    public Long getTimeBucket() {
        return getDataLong(2);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(2, timeBucket);
    }

    public Long getDuration() {
        return getDataLong(3);
    }

    public void setDuration(Long duration) {
        setDataLong(3, duration);
    }

    public Integer getInstanceId() {
        return getDataInteger(0);
    }

    public void setInstanceId(Integer instanceId) {
        setDataInteger(0, instanceId);
    }

    public Integer getPhrase() {
        return getDataInteger(1);
    }

    public void setPhrase(Integer phrase) {
        setDataInteger(1, phrase);
    }
}
