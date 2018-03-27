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

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.RemoteData;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.operator.CoverMergeOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonMergeOperation;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;

/**
 * @author peng-yongsheng
 */
public class ServiceName extends StreamData {

    private static final Column[] STRING_COLUMNS = {
        new Column(ServiceNameTable.COLUMN_ID, new NonMergeOperation()),
        new Column(ServiceNameTable.COLUMN_SERVICE_NAME, new CoverMergeOperation()),
    };

    private static final Column[] LONG_COLUMNS = {};

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(ServiceNameTable.COLUMN_APPLICATION_ID, new CoverMergeOperation()),
        new Column(ServiceNameTable.COLUMN_SERVICE_ID, new CoverMergeOperation()),
        new Column(ServiceNameTable.COLUMN_SRC_SPAN_TYPE, new CoverMergeOperation()),
    };

    private static final Column[] BYTE_COLUMNS = {};

    public ServiceName() {
        super(STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BYTE_COLUMNS);
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

    public String getServiceName() {
        return getDataString(1);
    }

    public void setServiceName(String serviceName) {
        setDataString(1, serviceName);
    }

    public int getApplicationId() {
        return getDataInteger(0);
    }

    public void setApplicationId(int applicationId) {
        setDataInteger(0, applicationId);
    }

    public int getServiceId() {
        return getDataInteger(1);
    }

    public void setServiceId(int serviceId) {
        setDataInteger(1, serviceId);
    }

    public int getSrcSpanType() {
        return getDataInteger(2);
    }

    public void setSrcSpanType(int srcSpanType) {
        setDataInteger(2, srcSpanType);
    }

    public static class InstanceCreator implements RemoteDataRegisterService.RemoteDataInstanceCreator {
        @Override public RemoteData createInstance() {
            return new ServiceName();
        }
    }
}
