/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.skywalking.apm.collector.storage.dao.IApplicationCacheDAO;
import org.skywalking.apm.collector.storage.dao.IApplicationRegisterDAO;
import org.skywalking.apm.collector.storage.dao.ICpuMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ICpuMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.IGCMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IGlobalTraceUIDAO;
import org.skywalking.apm.collector.storage.dao.IInstPerformancePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IInstPerformanceUIDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceCacheDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceRegisterDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryPoolMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeComponentPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeComponentUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeMappingPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeMappingUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeReferenceUIDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentUIDAO;
import org.skywalking.apm.collector.storage.dao.IServiceEntryPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.skywalking.apm.collector.storage.dao.IServiceNameCacheDAO;
import org.skywalking.apm.collector.storage.dao.IServiceNameRegisterDAO;
import org.skywalking.apm.collector.storage.dao.IServiceReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;

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
        classes.add(IInstPerformancePersistenceDAO.class);
        classes.add(INodeComponentPersistenceDAO.class);
        classes.add(INodeMappingPersistenceDAO.class);
        classes.add(INodeReferencePersistenceDAO.class);
        classes.add(ISegmentCostPersistenceDAO.class);
        classes.add(ISegmentPersistenceDAO.class);
        classes.add(IServiceEntryPersistenceDAO.class);
        classes.add(IServiceReferencePersistenceDAO.class);

        classes.add(IInstanceHeartBeatPersistenceDAO.class);
    }

    private void addUiDAO(List<Class> classes) {
        classes.add(IInstanceUIDAO.class);

        classes.add(ICpuMetricUIDAO.class);
        classes.add(IGCMetricUIDAO.class);
        classes.add(IMemoryMetricUIDAO.class);
        classes.add(IMemoryPoolMetricUIDAO.class);

        classes.add(IGlobalTraceUIDAO.class);
        classes.add(IInstPerformanceUIDAO.class);
        classes.add(INodeComponentUIDAO.class);
        classes.add(INodeMappingUIDAO.class);
        classes.add(INodeReferenceUIDAO.class);
        classes.add(ISegmentCostUIDAO.class);
        classes.add(ISegmentUIDAO.class);
        classes.add(IServiceEntryUIDAO.class);
        classes.add(IServiceReferenceUIDAO.class);
    }
}
