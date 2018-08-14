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

package org.apache.skywalking.apm.collector.storage.table.alarm;

import org.apache.skywalking.apm.collector.core.data.*;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceAlarm extends StreamData implements Alarm {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(ServiceReferenceAlarmTable.ID, new NonMergeOperation()),
        new StringColumn(ServiceReferenceAlarmTable.ALARM_CONTENT, new CoverMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(ServiceReferenceAlarmTable.LAST_TIME_BUCKET, new CoverMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(ServiceReferenceAlarmTable.ALARM_TYPE, new NonMergeOperation()),
        new IntegerColumn(ServiceReferenceAlarmTable.SOURCE_VALUE, new NonMergeOperation()),
        new IntegerColumn(ServiceReferenceAlarmTable.FRONT_APPLICATION_ID, new NonMergeOperation()),
        new IntegerColumn(ServiceReferenceAlarmTable.BEHIND_APPLICATION_ID, new NonMergeOperation()),
        new IntegerColumn(ServiceReferenceAlarmTable.FRONT_INSTANCE_ID, new NonMergeOperation()),
        new IntegerColumn(ServiceReferenceAlarmTable.BEHIND_INSTANCE_ID, new NonMergeOperation()),
        new IntegerColumn(ServiceReferenceAlarmTable.FRONT_SERVICE_ID, new NonMergeOperation()),
        new IntegerColumn(ServiceReferenceAlarmTable.BEHIND_SERVICE_ID, new NonMergeOperation()),
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public ServiceReferenceAlarm() {
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

    @Override
    public Integer getAlarmType() {
        return getDataInteger(0);
    }

    @Override
    public void setAlarmType(Integer alarmType) {
        setDataInteger(0, alarmType);
    }

    @Override
    public Integer getSourceValue() {
        return getDataInteger(1);
    }

    @Override
    public void setSourceValue(Integer sourceValue) {
        setDataInteger(1, sourceValue);
    }

    public Integer getFrontApplicationId() {
        return getDataInteger(2);
    }

    public void setFrontApplicationId(Integer frontApplicationId) {
        setDataInteger(2, frontApplicationId);
    }

    public Integer getBehindApplicationId() {
        return getDataInteger(3);
    }

    public void setBehindApplicationId(Integer behindApplicationId) {
        setDataInteger(3, behindApplicationId);
    }

    public Integer getFrontInstanceId() {
        return getDataInteger(4);
    }

    public void setFrontInstanceId(Integer frontInstanceId) {
        setDataInteger(4, frontInstanceId);
    }

    public Integer getBehindInstanceId() {
        return getDataInteger(5);
    }

    public void setBehindInstanceId(Integer behindInstanceId) {
        setDataInteger(5, behindInstanceId);
    }

    public Integer getFrontServiceId() {
        return getDataInteger(6);
    }

    public void setFrontServiceId(Integer frontServiceId) {
        setDataInteger(6, frontServiceId);
    }

    public Integer getBehindServiceId() {
        return getDataInteger(7);
    }

    public void setBehindServiceId(Integer behindServiceId) {
        setDataInteger(7, behindServiceId);
    }

    @Override
    public Long getLastTimeBucket() {
        return getDataLong(0);
    }

    @Override
    public void setLastTimeBucket(Long lastTimeBucket) {
        setDataLong(0, lastTimeBucket);
    }

    @Override
    public String getAlarmContent() {
        return getDataString(1);
    }

    @Override
    public void setAlarmContent(String alarmContent) {
        setDataString(1, alarmContent);
    }

    public static class InstanceCreator implements RemoteDataRegisterService.RemoteDataInstanceCreator {
        @Override public RemoteData createInstance() {
            return new ServiceReferenceAlarm();
        }
    }
}
