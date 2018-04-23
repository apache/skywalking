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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentDurationPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
