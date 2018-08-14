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

package org.apache.skywalking.apm.collector.analysis.register.provider.service;

import org.apache.skywalking.apm.collector.analysis.register.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.register.define.service.AgentOsInfo;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class InstanceIDService implements IInstanceIDService {

    private static final Logger logger = LoggerFactory.getLogger(InstanceIDService.class);

    private final ModuleManager moduleManager;
    private InstanceCacheService instanceCacheService;
    private Graph<Instance> instanceRegisterGraph;
    private ApplicationCacheService applicationCacheService;

    public InstanceIDService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private InstanceCacheService getInstanceCacheService() {
        if (isNull(instanceCacheService)) {
            instanceCacheService = moduleManager.find(CacheModule.NAME).getService(InstanceCacheService.class);
        }
        return instanceCacheService;
    }

    private Graph<Instance> getInstanceRegisterGraph() {
        if (isNull(instanceRegisterGraph)) {
            this.instanceRegisterGraph = GraphManager.INSTANCE.createIfAbsent(GraphIdDefine.INSTANCE_REGISTER_GRAPH_ID, Instance.class);
        }
        return instanceRegisterGraph;
    }

    private ApplicationCacheService getApplicationCacheService() {
        if (isNull(applicationCacheService)) {
            this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        }
        return applicationCacheService;
    }

    @Override public int getOrCreateByAgentUUID(int applicationId, String agentUUID, long registerTime, AgentOsInfo osInfo) {
        if (logger.isDebugEnabled()) {
            logger.debug("get or getOrCreate instance id by agent UUID, application id: {}, agentUUID: {}, registerTime: {}, osInfo: {}", applicationId, agentUUID, registerTime, osInfo);
        }

        int instanceId = getInstanceCacheService().getInstanceIdByAgentUUID(applicationId, agentUUID);

        if (instanceId == 0) {
            Instance instance = new Instance();
            instance.setId("0");
            instance.setApplicationId(applicationId);
            instance.setApplicationCode(getApplicationCacheService().getApplicationById(applicationId).getApplicationCode());
            instance.setAgentUUID(agentUUID);
            instance.setRegisterTime(registerTime);
            instance.setHeartBeatTime(registerTime);
            instance.setInstanceId(0);
            instance.setOsInfo(osInfo.toString());
            instance.setIsAddress(BooleanUtils.FALSE);
            instance.setAddressId(Const.NONE);

            getInstanceRegisterGraph().start(instance);
        }
        return instanceId;
    }

    @Override public int getOrCreateByAddressId(int applicationId, int addressId, long registerTime) {
        if (logger.isDebugEnabled()) {
            logger.debug("get or getOrCreate instance id by address id, application id: {}, address id: {}, registerTime: {}", applicationId, addressId, registerTime);
        }

        int instanceId = getInstanceCacheService().getInstanceIdByAddressId(applicationId, addressId);

        if (instanceId == 0) {
            Instance instance = new Instance();
            instance.setId("0");
            instance.setApplicationId(applicationId);
            instance.setApplicationCode(getApplicationCacheService().getApplicationById(applicationId).getApplicationCode());
            instance.setAgentUUID(Const.EMPTY_STRING);
            instance.setRegisterTime(registerTime);
            instance.setHeartBeatTime(registerTime);
            instance.setInstanceId(0);
            instance.setOsInfo(Const.EMPTY_STRING);
            instance.setIsAddress(BooleanUtils.TRUE);
            instance.setAddressId(addressId);

            getInstanceRegisterGraph().start(instance);
        }
        return instanceId;
    }
}
