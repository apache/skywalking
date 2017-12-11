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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.ICpuMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.dao.IAlertingListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;

/**
 * @author peng-yongsheng
 */
public class StorageModule extends Module {

    public static final String NAME = "storage";

    @Override public String name() {
        return NAME;
    }

    @Override public Class[] services() {
        List<Class> classes = new ArrayList<>();
        classes.add(IBatchDAO.class);

        addCacheDAO(classes);
        addRegisterDAO(classes);
        addPersistenceDAO(classes);
        addUiDAO(classes);
        addAlertingDAO(classes);

        return classes.toArray(new Class[] {});
    }

    private void addCacheDAO(List<Class> classes) {
        classes.add(IApplicationCacheDAO.class);
        classes.add(IInstanceCacheDAO.class);
        classes.add(IServiceNameCacheDAO.class);
    }

    private void addRegisterDAO(List<Class> classes) {
        classes.add(IApplicationRegisterDAO.class);
        classes.add(IInstanceRegisterDAO.class);
        classes.add(IServiceNameRegisterDAO.class);
    }

    private void addPersistenceDAO(List<Class> classes) {
        classes.add(ICpuMetricPersistenceDAO.class);
        classes.add(IGCMetricPersistenceDAO.class);
        classes.add(IMemoryMetricPersistenceDAO.class);
        classes.add(IMemoryPoolMetricPersistenceDAO.class);

        classes.add(IGlobalTracePersistenceDAO.class);
        classes.add(IInstanceMetricPersistenceDAO.class);
        classes.add(IApplicationComponentPersistenceDAO.class);
        classes.add(IApplicationMappingPersistenceDAO.class);
        classes.add(IApplicationMetricPersistenceDAO.class);
        classes.add(IApplicationReferenceMetricPersistenceDAO.class);
        classes.add(ISegmentCostPersistenceDAO.class);
        classes.add(ISegmentPersistenceDAO.class);
        classes.add(IServiceEntryPersistenceDAO.class);
        classes.add(IServiceMetricPersistenceDAO.class);
        classes.add(IServiceReferenceMetricPersistenceDAO.class);

        classes.add(IInstanceHeartBeatPersistenceDAO.class);
    }

    private void addUiDAO(List<Class> classes) {
        classes.add(IInstanceUIDAO.class);

        classes.add(ICpuMetricUIDAO.class);
        classes.add(IGCMetricUIDAO.class);
        classes.add(IMemoryMetricUIDAO.class);
        classes.add(IMemoryPoolMetricUIDAO.class);

        classes.add(IGlobalTraceUIDAO.class);
        classes.add(IInstanceMetricUIDAO.class);
        classes.add(IApplicationComponentUIDAO.class);
        classes.add(IApplicationMappingUIDAO.class);
        classes.add(IApplicationReferenceMetricUIDAO.class);
        classes.add(ISegmentCostUIDAO.class);
        classes.add(ISegmentUIDAO.class);
        classes.add(IServiceEntryUIDAO.class);
        classes.add(IServiceReferenceUIDAO.class);
    }

    private void addAlertingDAO(List<Class> classes) {
        classes.add(IAlertingListPersistenceDAO.class);
    }
}
