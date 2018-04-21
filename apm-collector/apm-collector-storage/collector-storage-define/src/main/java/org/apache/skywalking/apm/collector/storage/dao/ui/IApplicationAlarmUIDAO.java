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

package org.apache.skywalking.apm.collector.storage.dao.ui;

import java.text.ParseException;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IApplicationAlarmUIDAO extends DAO {

    /**
     * <p>SQL as: select APPLICATION_ID, ALARM_CONTENT, LAST_TIME_BUCKET, ALARM_TYPE from APPLICATION_ALARM
     * where LAST_TIME_BUCKET ge ${startTimeBucket} and LAST_TIME_BUCKET le ${endTimeBucket}
     * and ALARM_CONTENT like '%{keyword}%'
     * <p>Note: keyword maybe not given
     *
     * @param keyword fuzzy query
     * @param startTimeBucket start time bucket
     * @param endTimeBucket end time bucket
     * @param limit limits the number of rows returned by the query
     * @param from specified how many rows to skip
     * @return application alarm items
     * @throws ParseException alarm time parse exception
     */
    Alarm loadAlarmList(String keyword, long startTimeBucket, long endTimeBucket, int limit,
        int from) throws ParseException;
}
