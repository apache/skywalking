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

package org.apache.skywalking.apm.collector.storage.table.application;

import org.apache.skywalking.apm.collector.core.data.*;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;

/**
 * @author peng-yongsheng
 */
public class ApplicationMapping extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(ApplicationMappingTable.ID, new NonMergeOperation()),
        new StringColumn(ApplicationMappingTable.METRIC_ID, new NonMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(ApplicationMappingTable.TIME_BUCKET, new CoverMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(ApplicationMappingTable.APPLICATION_ID, new CoverMergeOperation()),
        new IntegerColumn(ApplicationMappingTable.MAPPING_APPLICATION_ID, new CoverMergeOperation()),
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public ApplicationMapping() {
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

    public int getApplicationId() {
        return getDataInteger(0);
    }

    public void setApplicationId(int applicationId) {
        setDataInteger(0, applicationId);
    }

    public int getMappingApplicationId() {
        return getDataInteger(1);
    }

    public void setMappingApplicationId(int mappingApplicationId) {
        setDataInteger(1, mappingApplicationId);
    }

    public long getTimeBucket() {
        return getDataLong(0);
    }

    public void setTimeBucket(long timeBucket) {
        setDataLong(0, timeBucket);
    }

    public static class InstanceCreator implements RemoteDataRegisterService.RemoteDataInstanceCreator {
        @Override public RemoteData createInstance() {
            return new ApplicationMapping();
        }
    }
}
