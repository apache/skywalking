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

package org.apache.skywalking.apm.collector.storage.table.register;

import org.apache.skywalking.apm.collector.core.data.*;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;

/**
 * @author peng-yongsheng
 */
public class Application extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(ApplicationTable.ID, new NonMergeOperation()),
        new StringColumn(ApplicationTable.APPLICATION_CODE, new CoverMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(ApplicationTable.APPLICATION_ID, new CoverMergeOperation()),
        new IntegerColumn(ApplicationTable.ADDRESS_ID, new CoverMergeOperation()),
        new IntegerColumn(ApplicationTable.IS_ADDRESS, new CoverMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public Application() {
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

    public String getApplicationCode() {
        return getDataString(1);
    }

    public void setApplicationCode(String applicationCode) {
        setDataString(1, applicationCode);
    }

    public int getApplicationId() {
        return getDataInteger(0);
    }

    public void setApplicationId(int applicationId) {
        setDataInteger(0, applicationId);
    }

    public int getAddressId() {
        return getDataInteger(1);
    }

    public void setAddressId(int addressId) {
        setDataInteger(1, addressId);
    }

    public int getIsAddress() {
        return getDataInteger(2);
    }

    public void setIsAddress(int isAddress) {
        setDataInteger(2, isAddress);
    }

    public static class InstanceCreator implements RemoteDataRegisterService.RemoteDataInstanceCreator {
        @Override public RemoteData createInstance() {
            return new Application();
        }
    }
}
