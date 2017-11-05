/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.table.segment;

import org.skywalking.apm.collector.core.data.Column;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class SegmentCost extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(SegmentCostTable.COLUMN_ID, new NonOperation()),
        new Column(SegmentCostTable.COLUMN_SEGMENT_ID, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_SERVICE_NAME, new CoverOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(SegmentCostTable.COLUMN_COST, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_START_TIME, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_END_TIME, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_TIME_BUCKET, new CoverOperation()),
    };
    private static final Column[] DOUBLE_COLUMNS = {};
    private static final Column[] INTEGER_COLUMNS = {
        new Column(SegmentCostTable.COLUMN_APPLICATION_ID, new CoverOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {
        new Column(SegmentCostTable.COLUMN_IS_ERROR, new CoverOperation()),
    };
    private static final Column[] BYTE_COLUMNS = {};

    public SegmentCost(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
    }

    public String getSegmentId() {
        return getDataString(1);
    }

    public String getServiceName() {
        return getDataString(2);
    }

    public Long getCost() {
        return getDataLong(0);
    }

    public Long getStartTime() {
        return getDataLong(1);
    }

    public Long getEndTime() {
        return getDataLong(2);
    }

    public Long getTimeBucket() {
        return getDataLong(3);
    }

    public Integer getApplicationId() {
        return getDataInteger(0);
    }

    public Boolean getIsError() {
        return getDataBoolean(0);
    }
}
