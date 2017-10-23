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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class MultipleModuleInstaller extends CommonModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(MultipleModuleInstaller.class);

    public MultipleModuleInstaller() {
        moduleDefines = new LinkedList<>();
    }

    private List<ModuleDefine> moduleDefines;
    private ServerHolder serverHolder;

    @Override public final void injectServerHolder(ServerHolder serverHolder) {
        this.serverHolder = serverHolder;
    }

    @Override public final void preInstall() throws DefineException, ConfigException, ServerException {
        logger.info("install module group: {}", groupName());
        Map<String, Map> moduleConfig = getModuleConfig();
        Map<String, ModuleDefine> moduleDefineMap = getModuleDefineMap();

        Iterator<Map.Entry<String, ModuleDefine>> moduleDefineIterator = moduleDefineMap.entrySet().iterator();
        while (moduleDefineIterator.hasNext()) {
            Map.Entry<String, ModuleDefine> moduleDefineEntry = moduleDefineIterator.next();
            logger.info("module {} initialize", moduleDefineEntry.getKey());
            moduleDefineEntry.getValue().configParser().parse(moduleConfig.get(moduleDefineEntry.getKey()));
            moduleDefines.add(moduleDefineEntry.getValue());
            serverHolder.holdServer(moduleDefineEntry.getValue().server(), moduleDefineEntry.getValue().handlerList());
        }
    }

    @Override public void install() throws DefineException, ConfigException, ServerException, ClientException {
        CollectorContextHelper.INSTANCE.putContext(moduleContext());
        for (ModuleDefine moduleDefine : moduleDefines) {
            moduleDefine.initializeOtherContext();

            if (moduleDefine instanceof ClusterDataListenerDefine) {
                ClusterDataListenerDefine listenerDefine = (ClusterDataListenerDefine)moduleDefine;
                if (ObjectUtils.isNotEmpty(listenerDefine.listener()) && ObjectUtils.isNotEmpty(moduleDefine.registration())) {
                    logger.info("add group: {}, module: {}, listener into cluster data monitor", moduleDefine.group(), moduleDefine.name());
                    CollectorContextHelper.INSTANCE.getClusterModuleContext().getDataMonitor().addListener(listenerDefine.listener(), moduleDefine.registration());
                }
            }
        }
    }

    @Override public void onAfterInstall() throws CollectorException {

    }
}
