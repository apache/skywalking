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
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.joda.time.DateTime;
import org.joda.time.Seconds;

/**
 * @author peng-yongsheng
 */
class SecondBetweenService {

    private final IInstanceUIDAO instanceUIDAO;

    SecondBetweenService(ModuleManager moduleManager) {
        this.instanceUIDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceUIDAO.class);
    }

    int calculate(int applicationId, long startSecondTimeBucket,
        long endSecondTimeBucket) throws ParseException {
        long registerTime = instanceUIDAO.getEarliestRegisterTime(applicationId);
        if (startSecondTimeBucket < registerTime) {
            startSecondTimeBucket = registerTime;
        }

        long heartBeatTime = instanceUIDAO.getLatestHeartBeatTime(applicationId);
        if (endSecondTimeBucket > heartBeatTime) {
            endSecondTimeBucket = heartBeatTime;
        }

        Date startDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(String.valueOf(startSecondTimeBucket));
        Date endDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(String.valueOf(endSecondTimeBucket));

        int seconds = Seconds.secondsBetween(new DateTime(startDate), new DateTime(endDate)).getSeconds();
        if (seconds == 0) {
            seconds = 1;
        }
        return seconds;
    }
}
