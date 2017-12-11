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
import org.apache.skywalking.apm.collector.core.data.Data;
import org.apache.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class GCMetric extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(GCMetricTable.COLUMN_ID, new NonOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(GCMetricTable.COLUMN_COUNT, new CoverOperation()),
        new Column(GCMetricTable.COLUMN_TIME, new CoverOperation()),
        new Column(GCMetricTable.COLUMN_TIME_BUCKET, new CoverOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {
    };

    private static final Column[] INTEGER_COLUMNS = {
        new Column(GCMetricTable.COLUMN_INSTANCE_ID, new CoverOperation()),
        new Column(GCMetricTable.COLUMN_PHRASE, new CoverOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};
    private static final Column[] BYTE_COLUMNS = {};

    public GCMetric(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
    }

    public Long getCount() {
        return getDataLong(0);
    }

    public void setCount(Long count) {
        setDataLong(0, count);
    }

    public Long getTime() {
        return getDataLong(1);
    }

    public void setTime(Long time) {
        setDataLong(1, time);
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
