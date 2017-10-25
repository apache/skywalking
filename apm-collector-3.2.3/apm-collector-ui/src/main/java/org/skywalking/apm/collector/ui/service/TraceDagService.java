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
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.INodeComponentDAO;
import org.skywalking.apm.collector.ui.dao.INodeMappingDAO;
import org.skywalking.apm.collector.ui.dao.INodeReferenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceDagService {

    private final Logger logger = LoggerFactory.getLogger(TraceDagService.class);

    public JsonObject load(long startTime, long endTime) {
        logger.debug("startTime: {}, endTime: {}", startTime, endTime);
        INodeComponentDAO nodeComponentDAO = (INodeComponentDAO)DAOContainer.INSTANCE.get(INodeComponentDAO.class.getName());
        JsonArray nodeComponentArray = nodeComponentDAO.load(startTime, endTime);

        INodeMappingDAO nodeMappingDAO = (INodeMappingDAO)DAOContainer.INSTANCE.get(INodeMappingDAO.class.getName());
        JsonArray nodeMappingArray = nodeMappingDAO.load(startTime, endTime);

        INodeReferenceDAO nodeRefSumDAO = (INodeReferenceDAO)DAOContainer.INSTANCE.get(INodeReferenceDAO.class.getName());
        JsonArray nodeRefSumArray = nodeRefSumDAO.load(startTime, endTime);

        TraceDagDataBuilder builder = new TraceDagDataBuilder();
        JsonObject traceDag = builder.build(nodeComponentArray, nodeMappingArray, nodeRefSumArray);

        return traceDag;
    }
}
