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
public class InstanceAlarm extends StreamData implements Alarm {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(InstanceAlarmTable.ID, new NonMergeOperation()),
        new StringColumn(InstanceAlarmTable.ALARM_CONTENT, new CoverMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(InstanceAlarmTable.LAST_TIME_BUCKET, new CoverMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(InstanceAlarmTable.ALARM_TYPE, new NonMergeOperation()),
        new IntegerColumn(InstanceAlarmTable.SOURCE_VALUE, new NonMergeOperation()),
        new IntegerColumn(InstanceAlarmTable.APPLICATION_ID, new NonMergeOperation()),
        new IntegerColumn(InstanceAlarmTable.INSTANCE_ID, new NonMergeOperation()),
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public InstanceAlarm() {
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

    public Integer getApplicationId() {
        return getDataInteger(2);
    }

    public void setApplicationId(Integer applicationId) {
        setDataInteger(2, applicationId);
    }

    public Integer getInstanceId() {
        return getDataInteger(3);
    }

    public void setInstanceId(Integer instanceId) {
        setDataInteger(3, instanceId);
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
            return new InstanceAlarm();
        }
    }
}
