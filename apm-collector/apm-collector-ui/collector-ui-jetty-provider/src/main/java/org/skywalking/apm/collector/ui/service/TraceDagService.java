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

package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.dao.INodeComponentUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeMappingUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeReferenceUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceDagService {

    private final Logger logger = LoggerFactory.getLogger(TraceDagService.class);

    private final INodeComponentUIDAO nodeComponentDAO;
    private final INodeMappingUIDAO nodeMappingDAO;
    private final INodeReferenceUIDAO nodeRefSumDAO;

    public TraceDagService(ModuleManager moduleManager) {
        this.nodeComponentDAO = moduleManager.find(StorageModule.NAME).getService(INodeComponentUIDAO.class);
        this.nodeMappingDAO = moduleManager.find(StorageModule.NAME).getService(INodeMappingUIDAO.class);
        this.nodeRefSumDAO = moduleManager.find(StorageModule.NAME).getService(INodeReferenceUIDAO.class);
    }

    public JsonObject load(long startTime, long endTime) {
        logger.debug("startTime: {}, endTime: {}", startTime, endTime);
        JsonArray nodeComponentArray = nodeComponentDAO.load(startTime, endTime);

        JsonArray nodeMappingArray = nodeMappingDAO.load(startTime, endTime);

        JsonArray nodeRefSumArray = nodeRefSumDAO.load(startTime, endTime);

        TraceDagDataBuilder builder = new TraceDagDataBuilder();
        JsonObject traceDag = builder.build(nodeComponentArray, nodeMappingArray, nodeRefSumArray);

        return traceDag;
    }
}
