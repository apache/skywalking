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


package org.apache.skywalking.apm.collector.storage.table.alerting;

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.Data;
import org.apache.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class AlertingList extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(AlertingListTable.COLUMN_ID, new NonOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(AlertingListTable.COLUMN_FIRST_TIME_BUCKET, new NonOperation()),
        new Column(AlertingListTable.COLUMN_LAST_TIME_BUCKET, new CoverOperation()),
    };
    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(AlertingListTable.COLUMN_LAYER, new CoverOperation()),
        new Column(AlertingListTable.COLUMN_LAYER_ID, new CoverOperation()),
        new Column(AlertingListTable.COLUMN_EXPECTED, new CoverOperation()),
        new Column(AlertingListTable.COLUMN_ACTUAL, new CoverOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {
        new Column(AlertingListTable.COLUMN_VALID, new CoverOperation()),
    };

    private static final Column[] BYTE_COLUMNS = {};

    public AlertingList(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
    }

    public Integer getLayer() {
        return getDataInteger(0);
    }

    public void setLayer(Integer layer) {
        setDataInteger(0, layer);
    }

    public Integer getLayerId() {
        return getDataInteger(1);
    }

    public void setLayerId(Integer layerId) {
        setDataInteger(1, layerId);
    }

    public Integer getExpected() {
        return getDataInteger(2);
    }

    public void setExpected(Integer expected) {
        setDataInteger(2, expected);
    }

    public Integer getActual() {
        return getDataInteger(3);
    }

    public void setActual(Integer actual) {
        setDataInteger(3, actual);
    }

    public Long getFirstTimeBucket() {
        return getDataLong(0);
    }

    public void setFirstTimeBucket(Long firstTimeBucket) {
        setDataLong(0, firstTimeBucket);
    }

    public Long getLastTimeBucket() {
        return getDataLong(1);
    }

    public void setLastTimeBucket(Long lastTimeBucket) {
        setDataLong(1, lastTimeBucket);
    }

    public Boolean getValid() {
        return getDataBoolean(0);
    }

    public void setValid(Boolean valid) {
        setDataBoolean(0, valid);
    }
}
