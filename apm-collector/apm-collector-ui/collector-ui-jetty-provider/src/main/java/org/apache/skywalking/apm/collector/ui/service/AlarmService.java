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

package org.apache.skywalking.apm.collector.ui.service;

import java.text.ParseException;
import java.util.List;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmListUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.overview.AlarmTrend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class AlarmService {

    private final Logger logger = LoggerFactory.getLogger(AlarmService.class);

    private final IInstanceUIDAO instanceDAO;
    private final IApplicationAlarmUIDAO applicationAlarmUIDAO;
    private final IInstanceAlarmUIDAO instanceAlarmUIDAO;
    private final IServiceAlarmUIDAO serviceAlarmUIDAO;
    private final IApplicationAlarmListUIDAO applicationAlarmListUIDAO;

    public AlarmService(ModuleManager moduleManager) {
        this.instanceDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceUIDAO.class);
        this.applicationAlarmUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmUIDAO.class);
        this.instanceAlarmUIDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceAlarmUIDAO.class);
        this.serviceAlarmUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceAlarmUIDAO.class);
        this.applicationAlarmListUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListUIDAO.class);
    }

    public Alarm loadApplicationAlarmList(String keyword, long start, long end,
        int limit, int from) throws ParseException {
        logger.debug("keyword: {}, start: {}, end: {}, limit: {}, from: {}", keyword, start, end, limit, from);
        return applicationAlarmUIDAO.loadAlarmList(keyword, start, end, limit, from);
    }

    public Alarm loadInstanceAlarmList(String keyword, long start, long end,
        int limit, int from) throws ParseException {
        logger.debug("keyword: {}, start: {}, end: {}, limit: {}, from: {}", keyword, start, end, limit, from);
        return instanceAlarmUIDAO.loadAlarmList(keyword, start, end, limit, from);
    }

    public Alarm loadServiceAlarmList(String keyword, long start, long end,
        int limit, int from) throws ParseException {
        logger.debug("keyword: {}, start: {}, end: {}, limit: {}, from: {}", keyword, start, end, limit, from);
        return serviceAlarmUIDAO.loadAlarmList(keyword, start, end, limit, from);
    }

    public AlarmTrend getApplicationAlarmTrend(Step step, long startTimeBucket, long endTimeBucket, long start,
        long end) throws ParseException {
        List<Application> applications = instanceDAO.getApplications(start, end);

        List<Integer> applicationNum = applicationAlarmListUIDAO.getAlarmedApplicationNum(step, startTimeBucket, endTimeBucket);
        AlarmTrend alarmTrend = new AlarmTrend();
        applicationNum.forEach(num -> {
            alarmTrend.getNumOfAlarmRate().add((num * 10000) / (applications.size()));
        });
        return alarmTrend;
    }
}
