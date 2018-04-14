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

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.operator.AddMergeOperation;
import org.apache.skywalking.apm.collector.core.data.operator.CoverMergeOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonMergeOperation;

/**
 * @author peng-yongsheng
 */
public class GCMetric extends StreamData {

    private static final Column[] STRING_COLUMNS = {
        new Column(GCMetricTable.ID, new NonMergeOperation()),
        new Column(GCMetricTable.METRIC_ID, new NonMergeOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(GCMetricTable.COUNT, new AddMergeOperation()),
        new Column(GCMetricTable.TIMES, new AddMergeOperation()),
        new Column(GCMetricTable.TIME_BUCKET, new CoverMergeOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {
    };

    private static final Column[] INTEGER_COLUMNS = {
        new Column(GCMetricTable.INSTANCE_ID, new CoverMergeOperation()),
        new Column(GCMetricTable.PHRASE, new CoverMergeOperation()),
    };

    private static final Column[] BYTE_COLUMNS = {};

    public GCMetric() {
        super(STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BYTE_COLUMNS);
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
