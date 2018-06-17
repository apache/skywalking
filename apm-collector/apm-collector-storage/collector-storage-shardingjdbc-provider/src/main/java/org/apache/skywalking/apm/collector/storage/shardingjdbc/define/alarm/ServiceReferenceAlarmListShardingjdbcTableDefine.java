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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.define.alarm;

import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcColumnDefine;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcTableDefine;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmListTable;

/**
 * @author linjiaqi
 */
public class ServiceReferenceAlarmListShardingjdbcTableDefine extends ShardingjdbcTableDefine {

    public ServiceReferenceAlarmListShardingjdbcTableDefine() {
        super(ServiceReferenceAlarmListTable.TABLE, ServiceReferenceAlarmListTable.TIME_BUCKET.getName());
    }

    @Override public void initialize() {
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.FRONT_APPLICATION_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.BEHIND_APPLICATION_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.FRONT_INSTANCE_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.BEHIND_INSTANCE_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.FRONT_SERVICE_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.BEHIND_SERVICE_ID, ShardingjdbcColumnDefine.Type.Int.name()));

        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.SOURCE_VALUE, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.ALARM_TYPE, ShardingjdbcColumnDefine.Type.Int.name()));

        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.ALARM_CONTENT, ShardingjdbcColumnDefine.Type.Varchar.name()));

        addColumn(new ShardingjdbcColumnDefine(ServiceReferenceAlarmListTable.TIME_BUCKET, ShardingjdbcColumnDefine.Type.Bigint.name()));
    }
}
