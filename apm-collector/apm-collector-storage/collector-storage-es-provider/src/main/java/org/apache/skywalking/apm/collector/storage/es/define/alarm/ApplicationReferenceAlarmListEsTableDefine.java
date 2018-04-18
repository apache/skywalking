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

package org.apache.skywalking.apm.collector.storage.es.define.alarm;

import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchColumnDefine;
import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchTableDefine;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmListTable;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceAlarmListEsTableDefine extends ElasticSearchTableDefine {

    public ApplicationReferenceAlarmListEsTableDefine() {
        super(ApplicationReferenceAlarmListTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 2;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(ApplicationReferenceAlarmListTable.FRONT_APPLICATION_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ApplicationReferenceAlarmListTable.BEHIND_APPLICATION_ID, ElasticSearchColumnDefine.Type.Integer.name()));

        addColumn(new ElasticSearchColumnDefine(ApplicationReferenceAlarmListTable.SOURCE_VALUE, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ApplicationReferenceAlarmListTable.ALARM_TYPE, ElasticSearchColumnDefine.Type.Integer.name()));

        addColumn(new ElasticSearchColumnDefine(ApplicationReferenceAlarmListTable.ALARM_CONTENT, ElasticSearchColumnDefine.Type.Text.name()));

        addColumn(new ElasticSearchColumnDefine(ApplicationReferenceAlarmListTable.TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
