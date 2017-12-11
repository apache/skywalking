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


package org.apache.skywalking.apm.collector.storage.table.service;

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.Data;
import org.apache.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class ServiceEntry extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(ServiceEntryTable.COLUMN_ID, new NonOperation()),
        new Column(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, new CoverOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(ServiceEntryTable.COLUMN_REGISTER_TIME, new NonOperation()),
        new Column(ServiceEntryTable.COLUMN_NEWEST_TIME, new CoverOperation()),
    };
    private static final Column[] DOUBLE_COLUMNS = {};
    private static final Column[] INTEGER_COLUMNS = {
        new Column(ServiceEntryTable.COLUMN_APPLICATION_ID, new CoverOperation()),
        new Column(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, new CoverOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};
    private static final Column[] BYTE_COLUMNS = {};

    public ServiceEntry(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
    }

    public String getEntryServiceName() {
        return getDataString(1);
    }

    public void setEntryServiceName(String entryServiceName) {
        setDataString(1, entryServiceName);
    }

    public Long getRegisterTime() {
        return getDataLong(0);
    }

    public void setRegisterTime(Long registerTime) {
        setDataLong(0, registerTime);
    }

    public Long getNewestTime() {
        return getDataLong(1);
    }

    public void setNewestTime(Long newestTime) {
        setDataLong(1, newestTime);
    }

    public Integer getApplicationId() {
        return getDataInteger(0);
    }

    public void setApplicationId(Integer applicationId) {
        setDataInteger(0, applicationId);
    }

    public Integer getEntryServiceId() {
        return getDataInteger(1);
    }

    public void setEntryServiceId(Integer entryServiceId) {
        setDataInteger(1, entryServiceId);
    }
}
