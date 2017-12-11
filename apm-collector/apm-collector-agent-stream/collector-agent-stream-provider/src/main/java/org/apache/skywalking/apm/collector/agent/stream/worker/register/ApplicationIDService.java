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

import org.apache.skywalking.apm.collector.agent.stream.service.register.IApplicationIDService;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.agent.stream.service.graph.RegisterStreamGraphDefine;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationIDService implements IApplicationIDService {

    private final Logger logger = LoggerFactory.getLogger(ApplicationIDService.class);

    private final ModuleManager moduleManager;
    private ApplicationCacheService applicationCacheService;
    private Graph<Application> applicationRegisterGraph;

    public ApplicationIDService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private Graph<Application> getApplicationRegisterGraph() {
        if (ObjectUtils.isEmpty(applicationRegisterGraph)) {
            this.applicationRegisterGraph = GraphManager.INSTANCE.createIfAbsent(RegisterStreamGraphDefine.APPLICATION_REGISTER_GRAPH_ID, Application.class);
        }
        return this.applicationRegisterGraph;
    }

    private ApplicationCacheService getApplicationCacheService() {
        if (ObjectUtils.isEmpty(applicationCacheService)) {
            this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        }
        return applicationCacheService;
    }

    public int getOrCreate(String applicationCode) {
        int applicationId = getApplicationCacheService().get(applicationCode);

        if (applicationId == 0) {
            Application application = new Application(applicationCode);
            application.setApplicationCode(applicationCode);
            application.setApplicationId(0);

            getApplicationRegisterGraph().start(application);
        }
        return applicationId;
    }
}
