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

package org.apache.skywalking.apm.collector.storage;

import java.util.*;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.dao.*;
import org.apache.skywalking.apm.collector.storage.dao.acp.*;
import org.apache.skywalking.apm.collector.storage.dao.alarm.*;
import org.apache.skywalking.apm.collector.storage.dao.amp.*;
import org.apache.skywalking.apm.collector.storage.dao.ampp.*;
import org.apache.skywalking.apm.collector.storage.dao.armp.*;
import org.apache.skywalking.apm.collector.storage.dao.cache.*;
import org.apache.skywalking.apm.collector.storage.dao.cpu.*;
import org.apache.skywalking.apm.collector.storage.dao.gc.*;
import org.apache.skywalking.apm.collector.storage.dao.imp.*;
import org.apache.skywalking.apm.collector.storage.dao.impp.*;
import org.apache.skywalking.apm.collector.storage.dao.irmp.*;
import org.apache.skywalking.apm.collector.storage.dao.memory.*;
import org.apache.skywalking.apm.collector.storage.dao.mpool.*;
import org.apache.skywalking.apm.collector.storage.dao.register.*;
import org.apache.skywalking.apm.collector.storage.dao.rtd.*;
import org.apache.skywalking.apm.collector.storage.dao.smp.*;
import org.apache.skywalking.apm.collector.storage.dao.srmp.*;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.ttl.ITTLConfigService;

/**
 * @author peng-yongsheng
 */
public class StorageModule extends ModuleDefine {

    public static final String NAME = "storage";

    @Override public String name() {
        return NAME;
    }

    @Override public Class[] services() {
        List<Class> classes = new ArrayList<>();
        classes.add(IBatchDAO.class);

        classes.add(ITTLConfigService.class);

        addCacheDAO(classes);
        addRegisterDAO(classes);
        addPersistenceDAO(classes);
        addUiDAO(classes);
        addAlarmDAO(classes);

        return classes.toArray(new Class[] {});
    }

    private void addCacheDAO(List<Class> classes) {
        classes.add(IApplicationCacheDAO.class);
        classes.add(IInstanceCacheDAO.class);
        classes.add(IServiceNameCacheDAO.class);
        classes.add(INetworkAddressCacheDAO.class);
    }

    private void addRegisterDAO(List<Class> classes) {
        classes.add(IApplicationRegisterDAO.class);
        classes.add(IInstanceRegisterDAO.class);
        classes.add(IServiceNameRegisterDAO.class);
        classes.add(INetworkAddressRegisterDAO.class);
    }

    private void addPersistenceDAO(List<Class> classes) {
        classes.add(ICpuMinuteMetricPersistenceDAO.class);
        classes.add(ICpuHourMetricPersistenceDAO.class);
        classes.add(ICpuDayMetricPersistenceDAO.class);
        classes.add(ICpuMonthMetricPersistenceDAO.class);

        classes.add(IGCMinuteMetricPersistenceDAO.class);
        classes.add(IGCHourMetricPersistenceDAO.class);
        classes.add(IGCDayMetricPersistenceDAO.class);
        classes.add(IGCMonthMetricPersistenceDAO.class);

        classes.add(IMemoryMinuteMetricPersistenceDAO.class);
        classes.add(IMemoryHourMetricPersistenceDAO.class);
        classes.add(IMemoryDayMetricPersistenceDAO.class);
        classes.add(IMemoryMonthMetricPersistenceDAO.class);

        classes.add(IMemoryPoolMinuteMetricPersistenceDAO.class);
        classes.add(IMemoryPoolHourMetricPersistenceDAO.class);
        classes.add(IMemoryPoolDayMetricPersistenceDAO.class);
        classes.add(IMemoryPoolMonthMetricPersistenceDAO.class);

        classes.add(IApplicationComponentMinutePersistenceDAO.class);
        classes.add(IApplicationComponentHourPersistenceDAO.class);
        classes.add(IApplicationComponentDayPersistenceDAO.class);
        classes.add(IApplicationComponentMonthPersistenceDAO.class);

        classes.add(IApplicationMappingMinutePersistenceDAO.class);
        classes.add(IApplicationMappingHourPersistenceDAO.class);
        classes.add(IApplicationMappingDayPersistenceDAO.class);
        classes.add(IApplicationMappingMonthPersistenceDAO.class);

        classes.add(IInstanceMappingMinutePersistenceDAO.class);
        classes.add(IInstanceMappingHourPersistenceDAO.class);
        classes.add(IInstanceMappingDayPersistenceDAO.class);
        classes.add(IInstanceMappingMonthPersistenceDAO.class);

        classes.add(IGlobalTracePersistenceDAO.class);
        classes.add(ISegmentDurationPersistenceDAO.class);
        classes.add(ISegmentPersistenceDAO.class);
        classes.add(IInstanceHeartBeatPersistenceDAO.class);
        classes.add(IServiceNameHeartBeatPersistenceDAO.class);

        classes.add(IResponseTimeDistributionMinutePersistenceDAO.class);
        classes.add(IResponseTimeDistributionHourPersistenceDAO.class);
        classes.add(IResponseTimeDistributionDayPersistenceDAO.class);
        classes.add(IResponseTimeDistributionMonthPersistenceDAO.class);

        classes.add(IApplicationMinuteMetricPersistenceDAO.class);
        classes.add(IApplicationHourMetricPersistenceDAO.class);
        classes.add(IApplicationDayMetricPersistenceDAO.class);
        classes.add(IApplicationMonthMetricPersistenceDAO.class);

        classes.add(IApplicationReferenceMinuteMetricPersistenceDAO.class);
        classes.add(IApplicationReferenceHourMetricPersistenceDAO.class);
        classes.add(IApplicationReferenceDayMetricPersistenceDAO.class);
        classes.add(IApplicationReferenceMonthMetricPersistenceDAO.class);

        classes.add(IServiceMinuteMetricPersistenceDAO.class);
        classes.add(IServiceHourMetricPersistenceDAO.class);
        classes.add(IServiceDayMetricPersistenceDAO.class);
        classes.add(IServiceMonthMetricPersistenceDAO.class);

        classes.add(IServiceReferenceMinuteMetricPersistenceDAO.class);
        classes.add(IServiceReferenceHourMetricPersistenceDAO.class);
        classes.add(IServiceReferenceDayMetricPersistenceDAO.class);
        classes.add(IServiceReferenceMonthMetricPersistenceDAO.class);

        classes.add(IInstanceMinuteMetricPersistenceDAO.class);
        classes.add(IInstanceHourMetricPersistenceDAO.class);
        classes.add(IInstanceDayMetricPersistenceDAO.class);
        classes.add(IInstanceMonthMetricPersistenceDAO.class);

        classes.add(IInstanceReferenceMinuteMetricPersistenceDAO.class);
        classes.add(IInstanceReferenceHourMetricPersistenceDAO.class);
        classes.add(IInstanceReferenceDayMetricPersistenceDAO.class);
        classes.add(IInstanceReferenceMonthMetricPersistenceDAO.class);
    }

    private void addUiDAO(List<Class> classes) {
        classes.add(IInstanceUIDAO.class);
        classes.add(INetworkAddressUIDAO.class);
        classes.add(IServiceNameServiceUIDAO.class);
        classes.add(IServiceMetricUIDAO.class);

        classes.add(ICpuMetricUIDAO.class);
        classes.add(IGCMetricUIDAO.class);
        classes.add(IMemoryMetricUIDAO.class);

        classes.add(IGlobalTraceUIDAO.class);
        classes.add(IResponseTimeDistributionUIDAO.class);
        classes.add(IInstanceMetricUIDAO.class);
        classes.add(IApplicationComponentUIDAO.class);
        classes.add(IApplicationMappingUIDAO.class);
        classes.add(IApplicationMetricUIDAO.class);
        classes.add(IApplicationReferenceMetricUIDAO.class);
        classes.add(ISegmentDurationUIDAO.class);
        classes.add(ISegmentUIDAO.class);
        classes.add(IServiceReferenceMetricUIDAO.class);

        classes.add(IApplicationAlarmUIDAO.class);
        classes.add(IInstanceAlarmUIDAO.class);
        classes.add(IServiceAlarmUIDAO.class);
        classes.add(IApplicationAlarmListUIDAO.class);
    }

    private void addAlarmDAO(List<Class> classes) {
        classes.add(IServiceReferenceAlarmPersistenceDAO.class);
        classes.add(IServiceReferenceAlarmListPersistenceDAO.class);
        classes.add(IInstanceReferenceAlarmPersistenceDAO.class);
        classes.add(IInstanceReferenceAlarmListPersistenceDAO.class);
        classes.add(IApplicationReferenceAlarmPersistenceDAO.class);
        classes.add(IApplicationReferenceAlarmListPersistenceDAO.class);

        classes.add(IServiceAlarmPersistenceDAO.class);
        classes.add(IServiceAlarmListPersistenceDAO.class);
        classes.add(IInstanceAlarmPersistenceDAO.class);
        classes.add(IInstanceAlarmListPersistenceDAO.class);
        classes.add(IApplicationAlarmPersistenceDAO.class);

        classes.add(IApplicationAlarmListMinutePersistenceDAO.class);
        classes.add(IApplicationAlarmListHourPersistenceDAO.class);
        classes.add(IApplicationAlarmListDayPersistenceDAO.class);
        classes.add(IApplicationAlarmListMonthPersistenceDAO.class);
    }
}
