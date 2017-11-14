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

package org.skywalking.apm.collector.agent.stream.worker.register;

import org.skywalking.apm.collector.agent.stream.graph.RegisterStreamGraph;
import org.skywalking.apm.collector.cache.CacheModule;
import org.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.module.ModuleNotFoundException;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.dao.IInstanceStreamDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceIDService {

    private final Logger logger = LoggerFactory.getLogger(InstanceIDService.class);

    private final ModuleManager moduleManager;

    public InstanceIDService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @SuppressWarnings("unchecked")
    public int getOrCreate(int applicationId, String agentUUID, long registerTime,
        String osInfo) throws ModuleNotFoundException, ServiceNotProvidedException {
        logger.debug("get or create instance id, application id: {}, agentUUID: {}, registerTime: {}, osInfo: {}", applicationId, agentUUID, registerTime, osInfo);
        InstanceCacheService service = moduleManager.find(CacheModule.NAME).getService(InstanceCacheService.class);
        int instanceId = service.getInstanceId(applicationId, agentUUID);

        if (instanceId == 0) {
            Instance instance = new Instance("0");
            instance.setApplicationId(applicationId);
            instance.setAgentUUID(agentUUID);
            instance.setRegisterTime(registerTime);
            instance.setHeartBeatTime(registerTime);
            instance.setInstanceId(0);
            instance.setOsInfo(osInfo);

            GraphManager.INSTANCE.findGraph(RegisterStreamGraph.INSTANCE_REGISTER_GRAPH_ID).start(instance);
        }
        return instanceId;
    }

    public void recover(int instanceId, int applicationId, long registerTime,
        String osInfo) throws ModuleNotFoundException, ServiceNotProvidedException {
        logger.debug("instance recover, instance id: {}, application id: {}, register time: {}", instanceId, applicationId, registerTime);
        DAOService daoService = moduleManager.find(StorageModule.NAME).getService(DAOService.class);
        IInstanceStreamDAO dao = (IInstanceStreamDAO)daoService.get(IInstanceStreamDAO.class);

        Instance instance = new Instance(String.valueOf(instanceId));
        instance.setApplicationId(applicationId);
        instance.setAgentUUID("");
        instance.setRegisterTime(registerTime);
        instance.setHeartBeatTime(registerTime);
        instance.setInstanceId(instanceId);
        instance.setOsInfo(osInfo);
        dao.save(instance);
    }
}
