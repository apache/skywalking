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

package org.skywalking.apm.collector.storage.table.instance;

import org.skywalking.apm.collector.core.data.Column;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.core.data.operator.AddOperation;
import org.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class InstPerformance extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(InstPerformanceTable.COLUMN_ID, new NonOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(InstPerformanceTable.COLUMN_COST_TOTAL, new AddOperation()),
        new Column(InstPerformanceTable.COLUMN_TIME_BUCKET, new CoverOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(InstPerformanceTable.COLUMN_APPLICATION_ID, new CoverOperation()),
        new Column(InstPerformanceTable.COLUMN_INSTANCE_ID, new CoverOperation()),
        new Column(InstPerformanceTable.COLUMN_CALLS, new AddOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};
    private static final Column[] BYTE_COLUMNS = {};

    public InstPerformance(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
    }

    public Long getCostTotal() {
        return getDataLong(0);
    }

    public void setCostTotal(Long costTotal) {
        setDataLong(0, costTotal);
    }

    public Long getTimeBucket() {
        return getDataLong(1);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(1, timeBucket);
    }

    public Integer getApplicationId() {
        return getDataInteger(0);
    }

    public void setApplicationId(Integer applicationId) {
        setDataInteger(0, applicationId);
    }

    public Integer getInstanceId() {
        return getDataInteger(1);
    }

    public void setInstanceId(Integer instanceId) {
        setDataInteger(1, instanceId);
    }

    public Integer getCalls() {
        return getDataInteger(2);
    }

    public void setCalls(Integer calls) {
        setDataInteger(2, calls);
    }
}
