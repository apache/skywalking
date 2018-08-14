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

package org.apache.skywalking.apm.collector.storage.es;

import java.util.concurrent.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.*;
import org.apache.skywalking.apm.collector.storage.dao.acp.*;
import org.apache.skywalking.apm.collector.storage.dao.alarm.*;
import org.apache.skywalking.apm.collector.storage.dao.amp.*;
import org.apache.skywalking.apm.collector.storage.dao.ampp.*;
import org.apache.skywalking.apm.collector.storage.dao.armp.*;
import org.apache.skywalking.apm.collector.storage.dao.cpu.*;
import org.apache.skywalking.apm.collector.storage.dao.gc.*;
import org.apache.skywalking.apm.collector.storage.dao.imp.*;
import org.apache.skywalking.apm.collector.storage.dao.impp.*;
import org.apache.skywalking.apm.collector.storage.dao.irmp.*;
import org.apache.skywalking.apm.collector.storage.dao.memory.*;
import org.apache.skywalking.apm.collector.storage.dao.mpool.*;
import org.apache.skywalking.apm.collector.storage.dao.rtd.*;
import org.apache.skywalking.apm.collector.storage.dao.smp.*;
import org.apache.skywalking.apm.collector.storage.dao.srmp.*;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.joda.time.DateTime;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class DataTTLKeeperTimer {
    private static final Logger logger = LoggerFactory.getLogger(StorageModuleEsProvider.class);

    private final ModuleManager moduleManager;
    private final StorageModuleEsNamingListener namingListener;
    private final String selfAddress;
    private final StorageModuleEsConfig config;

    DataTTLKeeperTimer(ModuleManager moduleManager,
        StorageModuleEsNamingListener namingListener, String selfAddress, StorageModuleEsConfig config) {
        this.moduleManager = moduleManager;
        this.namingListener = namingListener;
        this.selfAddress = selfAddress;
        this.config = config;
    }

    void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new RunnableWithExceptionProtection(this::delete,
                t -> logger.error("Remove data in background failure.", t)), 1, 5, TimeUnit.MINUTES);
    }

    private void delete() {
        String firstAddressInCluster = namingListener.getAddresses().iterator().next();
        if (!firstAddressInCluster.equals(selfAddress)) {
            logger.info("Current address {} isn't same with the selected first address is {}. Skip.", selfAddress, firstAddressInCluster);
            return;
        }

        TimeBuckets timeBuckets = convertTimeBucket(new DateTime());
        logger.info("Beginning to remove expired metrics from the storage.");
        logger.info("Metrics in minute dimension before {}, are going to be removed.", timeBuckets.minuteTimeBucketBefore);
        logger.info("Metrics in hour dimension before {}, are going to be removed.", timeBuckets.hourTimeBucketBefore);
        logger.info("Metrics in day dimension before {}, are going to be removed.", timeBuckets.dayTimeBucketBefore);
        logger.info("Metrics in month dimension before {}, are going to be removed.", timeBuckets.monthTimeBucketBefore);

        deleteJVMRelatedData(timeBuckets);
        deleteTraceRelatedData(timeBuckets);
        deleteAlarmRelatedData(timeBuckets);
    }

    TimeBuckets convertTimeBucket(DateTime currentTime) {
        TimeBuckets timeBuckets = new TimeBuckets();

        timeBuckets.traceDataBefore = Long.valueOf(currentTime.plusMinutes(0 - config.getTraceDataTTL()).toString("yyyyMMddHHmm"));
        timeBuckets.minuteTimeBucketBefore = Long.valueOf(currentTime.plusMinutes(0 - config.getMinuteMetricDataTTL()).toString("yyyyMMddHHmm"));
        timeBuckets.hourTimeBucketBefore = Long.valueOf(currentTime.plusHours(0 - config.getHourMetricDataTTL()).toString("yyyyMMddHH"));
        timeBuckets.dayTimeBucketBefore = Long.valueOf(currentTime.plusDays(0 - config.getDayMetricDataTTL()).toString("yyyyMMdd"));
        timeBuckets.monthTimeBucketBefore = Long.valueOf(currentTime.plusMonths(0 - config.getMonthMetricDataTTL()).toString("yyyyMM"));

        return timeBuckets;
    }

    private void deleteAlarmRelatedData(TimeBuckets timeBuckets) {
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListMinutePersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListHourPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListDayPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListMonthPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IInstanceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IInstanceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IServiceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IServiceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
    }

    private void deleteJVMRelatedData(TimeBuckets timeBuckets) {
        moduleManager.find(StorageModule.NAME).getService(ICpuMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(ICpuHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(ICpuDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(ICpuMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IGCMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IGCHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IGCDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IGCMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IMemoryMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IMemoryHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IMemoryDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IMemoryMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);
    }

    private void deleteTraceRelatedData(TimeBuckets timeBuckets) {
        moduleManager.find(StorageModule.NAME).getService(IGlobalTracePersistenceDAO.class).deleteHistory(timeBuckets.traceDataBefore);
        moduleManager.find(StorageModule.NAME).getService(ISegmentDurationPersistenceDAO.class).deleteHistory(timeBuckets.traceDataBefore);
        moduleManager.find(StorageModule.NAME).getService(ISegmentPersistenceDAO.class).deleteHistory(timeBuckets.traceDataBefore);

        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentMinutePersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentHourPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentDayPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentMonthPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IApplicationMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingMinutePersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingHourPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingDayPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingMonthPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IInstanceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingMinutePersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingHourPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingDayPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingMonthPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionMinutePersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionHourPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionDayPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionMonthPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IServiceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IServiceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IServiceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IServiceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);

        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.minuteTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.hourTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.dayTimeBucketBefore);
        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.monthTimeBucketBefore);
    }

    class TimeBuckets {
        private long traceDataBefore;
        private long minuteTimeBucketBefore;
        private long hourTimeBucketBefore;
        private long dayTimeBucketBefore;
        private long monthTimeBucketBefore;
    }
}