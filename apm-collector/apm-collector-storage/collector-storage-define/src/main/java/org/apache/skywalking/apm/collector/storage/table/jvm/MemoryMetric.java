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
public class MemoryMetric extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(MemoryMetricTable.ID, new NonMergeOperation()),
        new StringColumn(MemoryMetricTable.METRIC_ID, new NonMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(MemoryMetricTable.INIT, new MinMergeOperation()),
        new LongColumn(MemoryMetricTable.MAX, new MaxMergeOperation()),
        new LongColumn(MemoryMetricTable.USED, new AddMergeOperation()),
        new LongColumn(MemoryMetricTable.COMMITTED, new AddMergeOperation()),
        new LongColumn(MemoryMetricTable.TIMES, new AddMergeOperation()),
        new LongColumn(MemoryMetricTable.TIME_BUCKET, new NonMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(MemoryMetricTable.INSTANCE_ID, new CoverMergeOperation()),
        new IntegerColumn(MemoryMetricTable.IS_HEAP, new CoverMergeOperation()),
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public MemoryMetric() {
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

    public Long getInit() {
        return getDataLong(0);
    }

    public void setInit(Long init) {
        setDataLong(0, init);
    }

    public Long getMax() {
        return getDataLong(1);
    }

    public void setMax(Long max) {
        setDataLong(1, max);
    }

    public Long getUsed() {
        return getDataLong(2);
    }

    public void setUsed(Long used) {
        setDataLong(2, used);
    }

    public Long getCommitted() {
        return getDataLong(3);
    }

    public void setCommitted(Long committed) {
        setDataLong(3, committed);
    }

    public Long getTimes() {
        return getDataLong(4);
    }

    public void setTimes(Long times) {
        setDataLong(4, times);
    }

    public Long getTimeBucket() {
        return getDataLong(5);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(5, timeBucket);
    }

    public Integer getInstanceId() {
        return getDataInteger(0);
    }

    public void setInstanceId(Integer instanceId) {
        setDataInteger(0, instanceId);
    }

    public Integer getIsHeap() {
        return getDataInteger(1);
    }

    public void setIsHeap(Integer isHeap) {
        setDataInteger(1, isHeap);
    }
}
