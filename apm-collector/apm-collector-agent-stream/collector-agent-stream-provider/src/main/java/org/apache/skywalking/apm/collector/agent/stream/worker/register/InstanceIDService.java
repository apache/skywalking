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


package org.apache.skywalking.apm.collector.agent.stream.worker.register;

import org.apache.skywalking.apm.collector.agent.stream.service.register.IInstanceIDService;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.agent.stream.service.graph.RegisterStreamGraphDefine;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceIDService implements IInstanceIDService {

    private final Logger logger = LoggerFactory.getLogger(InstanceIDService.class);

    private final ModuleManager moduleManager;
    private InstanceCacheService instanceCacheService;
    private Graph<Instance> instanceRegisterGraph;
    private IInstanceRegisterDAO instanceRegisterDAO;

    public InstanceIDService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private InstanceCacheService getInstanceCacheService() {
        if (ObjectUtils.isEmpty(instanceCacheService)) {
            instanceCacheService = moduleManager.find(CacheModule.NAME).getService(InstanceCacheService.class);
        }
        return instanceCacheService;
    }

    private Graph<Instance> getInstanceRegisterGraph() {
        if (ObjectUtils.isEmpty(instanceRegisterGraph)) {
            this.instanceRegisterGraph = GraphManager.INSTANCE.createIfAbsent(RegisterStreamGraphDefine.INSTANCE_REGISTER_GRAPH_ID, Instance.class);
        }
        return instanceRegisterGraph;
    }

    private IInstanceRegisterDAO getInstanceRegisterDAO() {
        if (ObjectUtils.isEmpty(instanceRegisterDAO)) {
            instanceRegisterDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceRegisterDAO.class);
        }
        return instanceRegisterDAO;
    }

    public int getOrCreate(int applicationId, String agentUUID, long registerTime, String osInfo) {
        logger.debug("get or create instance id, application id: {}, agentUUID: {}, registerTime: {}, osInfo: {}", applicationId, agentUUID, registerTime, osInfo);
        int instanceId = getInstanceCacheService().getInstanceId(applicationId, agentUUID);

        if (instanceId == 0) {
            Instance instance = new Instance("0");
            instance.setApplicationId(applicationId);
            instance.setAgentUUID(agentUUID);
            instance.setRegisterTime(registerTime);
            instance.setHeartBeatTime(registerTime);
            instance.setInstanceId(0);
            instance.setOsInfo(osInfo);

            getInstanceRegisterGraph().start(instance);
        }
        return instanceId;
    }

    public void recover(int instanceId, int applicationId, long registerTime, String osInfo) {
        logger.debug("instance recover, instance id: {}, application id: {}, register time: {}", instanceId, applicationId, registerTime);
        Instance instance = new Instance(String.valueOf(instanceId));
        instance.setApplicationId(applicationId);
        instance.setAgentUUID("");
        instance.setRegisterTime(registerTime);
        instance.setHeartBeatTime(registerTime);
        instance.setInstanceId(instanceId);
        instance.setOsInfo(osInfo);

        getInstanceRegisterDAO().save(instance);
    }
}
