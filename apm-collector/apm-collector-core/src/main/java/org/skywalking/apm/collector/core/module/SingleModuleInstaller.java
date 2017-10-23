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

package org.skywalking.apm.collector.core.module;

import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.cluster.ClusterModuleException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class SingleModuleInstaller extends CommonModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(SingleModuleInstaller.class);

    private ModuleDefine moduleDefine;
    private ServerHolder serverHolder;

    @Override public final void injectServerHolder(ServerHolder serverHolder) {
        this.serverHolder = serverHolder;
    }

    @Override public final void preInstall() throws DefineException, ConfigException, ServerException {
        logger.info("install module group: {}", groupName());
        Map<String, Map> moduleConfig = getModuleConfig();
        Map<String, ModuleDefine> moduleDefineMap = getModuleDefineMap();
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            if (moduleConfig.size() > 1) {
                throw new ClusterModuleException("single module, but configure multiple modules");
            }

            Map.Entry<String, Map> configEntry = moduleConfig.entrySet().iterator().next();
            if (moduleDefineMap.containsKey(configEntry.getKey())) {
                moduleDefine = moduleDefineMap.get(configEntry.getKey());
                moduleDefine.configParser().parse(configEntry.getValue());
            } else {
                throw new ClusterModuleException("module name incorrect, please check the module name in application.yml");
            }
        } else {
            logger.info("could not configure module, use the default");
            Iterator<Map.Entry<String, ModuleDefine>> moduleDefineIterator = moduleDefineMap.entrySet().iterator();

            boolean hasDefaultModule = false;
            while (moduleDefineIterator.hasNext()) {
                Map.Entry<String, ModuleDefine> moduleDefineEntry = moduleDefineIterator.next();
                if (moduleDefineEntry.getValue().defaultModule()) {
                    if (hasDefaultModule) {
                        throw new ClusterModuleException("single module, but configure multiple default module");
                    }
                    this.moduleDefine = moduleDefineEntry.getValue();
                    if (this.moduleDefine.configParser() != null) {
                        this.moduleDefine.configParser().parse(null);
                    }
                    hasDefaultModule = true;
                }
            }
        }
        serverHolder.holdServer(moduleDefine.server(), moduleDefine.handlerList());
    }

    @Override public void install() throws ClientException, DefineException, ConfigException, ServerException {
        if (!(moduleContext() instanceof ClusterModuleContext)) {
            CollectorContextHelper.INSTANCE.putContext(moduleContext());
        }
        moduleDefine.initializeOtherContext();

        if (moduleDefine instanceof ClusterDataListenerDefine) {
            ClusterDataListenerDefine listenerDefine = (ClusterDataListenerDefine)moduleDefine;
            if (ObjectUtils.isNotEmpty(listenerDefine.listener()) && ObjectUtils.isNotEmpty(moduleDefine.registration())) {
                CollectorContextHelper.INSTANCE.getClusterModuleContext().getDataMonitor().addListener(listenerDefine.listener(), moduleDefine.registration());
                logger.info("add group: {}, module: {}, listener into cluster data monitor", moduleDefine.group(), moduleDefine.name());
            }
        }
    }

    protected ModuleDefine getModuleDefine() {
        return moduleDefine;
    }
}
