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

import java.util.Calendar;
import java.util.concurrent.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
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
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class DataTTLKeeperTimer {
    private static final Logger logger = LoggerFactory.getLogger(StorageModuleEsProvider.class);

    private final ModuleManager moduleManager;
    private final StorageModuleEsNamingListener namingListener;
    private final String selfAddress;
    private final int daysBefore;

    DataTTLKeeperTimer(ModuleManager moduleManager,
        StorageModuleEsNamingListener namingListener, String selfAddress, int daysBefore) {
        this.moduleManager = moduleManager;
        this.namingListener = namingListener;
        this.selfAddress = selfAddress;
        this.daysBefore = daysBefore;
    }

    void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new RunnableWithExceptionProtection(this::delete,
                t -> logger.error("Remove data in background failure.", t)), 1, 8, TimeUnit.HOURS);
    }

    private void delete() {
        if (!namingListener.getAddresses().iterator().next().equals(selfAddress)) {
            return;
        }

        TimeBuckets timeBuckets = convertTimeBucket();

        deleteJVMRelatedData(timeBuckets);
        deleteTraceRelatedData(timeBuckets);
        deleteAlarmRelatedData(timeBuckets);
    }

    TimeBuckets convertTimeBucket() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.DAY_OF_MONTH, -daysBefore);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        long startTimestamp = calendar.getTimeInMillis();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);

        long endTimestamp = calendar.getTimeInMillis();

        TimeBuckets timeBuckets = new TimeBuckets();
        timeBuckets.startSecondTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        timeBuckets.endSecondTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);

        timeBuckets.startMinuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        timeBuckets.endMinuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);

        timeBuckets.startHourTimeBucket = TimeBucketUtils.INSTANCE.minuteToHour(timeBuckets.startMinuteTimeBucket);
        timeBuckets.endHourTimeBucket = TimeBucketUtils.INSTANCE.minuteToHour(timeBuckets.endMinuteTimeBucket);

        timeBuckets.startDayTimeBucket = TimeBucketUtils.INSTANCE.minuteToDay(timeBuckets.startMinuteTimeBucket);
        timeBuckets.endDayTimeBucket = TimeBucketUtils.INSTANCE.minuteToDay(timeBuckets.endMinuteTimeBucket);

        timeBuckets.startMonthTimeBucket = TimeBucketUtils.INSTANCE.minuteToMonth(timeBuckets.startMinuteTimeBucket);
        timeBuckets.endMonthTimeBucket = TimeBucketUtils.INSTANCE.minuteToMonth(timeBuckets.endMinuteTimeBucket);

        return timeBuckets;
    }

    private void deleteAlarmRelatedData(TimeBuckets timeBuckets) {
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListMinutePersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListHourPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListDayPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListMonthPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IInstanceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IInstanceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IServiceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IServiceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceAlarmPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceAlarmListPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
    }

    private void deleteJVMRelatedData(TimeBuckets timeBuckets) {
        moduleManager.find(StorageModule.NAME).getService(ICpuMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(ICpuHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(ICpuDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(ICpuMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IGCMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IGCHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IGCDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IGCMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IMemoryMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IMemoryHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IMemoryDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IMemoryMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IMemoryPoolMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);
    }

    private void deleteTraceRelatedData(TimeBuckets timeBuckets) {
        moduleManager.find(StorageModule.NAME).getService(IGlobalTracePersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(ISegmentDurationPersistenceDAO.class).deleteHistory(timeBuckets.startSecondTimeBucket, timeBuckets.endSecondTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(ISegmentPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentMinutePersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentHourPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentDayPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationComponentMonthPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IApplicationMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingMinutePersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingHourPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingDayPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationMappingMonthPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IInstanceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingMinutePersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingHourPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingDayPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceMappingMonthPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IInstanceReferenceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionMinutePersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionHourPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionDayPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionMonthPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IServiceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IServiceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IServiceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IServiceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);

        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMinuteMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMinuteTimeBucket, timeBuckets.endMinuteTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceHourMetricPersistenceDAO.class).deleteHistory(timeBuckets.startHourTimeBucket, timeBuckets.endHourTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceDayMetricPersistenceDAO.class).deleteHistory(timeBuckets.startDayTimeBucket, timeBuckets.endDayTimeBucket);
        moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMonthMetricPersistenceDAO.class).deleteHistory(timeBuckets.startMonthTimeBucket, timeBuckets.endMonthTimeBucket);
    }

    class TimeBuckets {
        private long startSecondTimeBucket;
        private long endSecondTimeBucket;

        private long startMinuteTimeBucket;
        private long endMinuteTimeBucket;

        private long startHourTimeBucket;
        private long endHourTimeBucket;

        private long startDayTimeBucket;
        private long endDayTimeBucket;

        private long startMonthTimeBucket;
        private long endMonthTimeBucket;
    }
}