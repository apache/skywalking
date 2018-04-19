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

package org.apache.skywalking.apm.collector.ui.query;

import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Pagination;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.service.AlarmService;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.apache.skywalking.apm.collector.ui.utils.PaginationUtils;

import java.text.ParseException;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class AlarmQuery implements Query {

    private final ModuleManager moduleManager;
    private AlarmService alarmService;

    public AlarmQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AlarmService getAlarmService() {
        if (isNull(alarmService)) {
            this.alarmService = new AlarmService(moduleManager);
        }
        return alarmService;
    }

    public Alarm loadAlarmList(String keyword, AlarmType alarmType, Duration duration,
        Pagination paging) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart()) / 100;
        long endTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd()) / 100;

        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

        switch (alarmType) {
            case APPLICATION:
                return getAlarmService().loadApplicationAlarmList(keyword, duration.getStep(), startTimeBucket, endTimeBucket, page.getLimit(), page.getFrom());
            case SERVER:
                return getAlarmService().loadInstanceAlarmList(keyword, duration.getStep(), startTimeBucket, endTimeBucket, page.getLimit(), page.getFrom());
            case SERVICE:
                return getAlarmService().loadServiceAlarmList(keyword, duration.getStep(), startTimeBucket, endTimeBucket, page.getLimit(), page.getFrom());
            default:
                return new Alarm();
        }
    }
}
