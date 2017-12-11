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


package org.apache.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceDagService {

    private final Logger logger = LoggerFactory.getLogger(TraceDagService.class);

    private final IApplicationComponentUIDAO applicationComponentUIDAO;
    private final IApplicationMappingUIDAO applicationMappingUIDAO;
    private final IApplicationReferenceMetricUIDAO applicationReferenceMetricUIDAO;
    private final ModuleManager moduleManager;

    public TraceDagService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.applicationComponentUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationComponentUIDAO.class);
        this.applicationMappingUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMappingUIDAO.class);
        this.applicationReferenceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMetricUIDAO.class);
    }

    public JsonObject load(long startTime, long endTime) {
        logger.debug("startTime: {}, endTime: {}", startTime, endTime);
        JsonArray applicationComponentArray = applicationComponentUIDAO.load(startTime, endTime);

        JsonArray applicationMappingArray = applicationMappingUIDAO.load(startTime, endTime);

        JsonArray applicationReferenceMetricArray = applicationReferenceMetricUIDAO.load(startTime, endTime);

        TraceDagDataBuilder builder = new TraceDagDataBuilder(moduleManager);
        JsonObject traceDag = builder.build(applicationComponentArray, applicationMappingArray, applicationReferenceMetricArray);

        return traceDag;
    }
}
