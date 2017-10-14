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
import org.skywalking.apm.collector.ui.dao.IServiceEntryDAO;
import org.skywalking.apm.collector.ui.dao.IServiceReferenceDAO;

/**
 * @author pengys5
 */
public class ServiceTreeService {

    public JsonObject loadEntryService(int applicationId, String entryServiceName, long startTime, long endTime,
        int from, int size) {
        IServiceEntryDAO serviceEntryDAO = (IServiceEntryDAO)DAOContainer.INSTANCE.get(IServiceEntryDAO.class.getName());
        return serviceEntryDAO.load(applicationId, entryServiceName, startTime, endTime, from, size);
    }

    public JsonArray loadServiceTree(int entryServiceId, long startTime, long endTime) {
        IServiceReferenceDAO serviceReferenceDAO = (IServiceReferenceDAO)DAOContainer.INSTANCE.get(IServiceReferenceDAO.class.getName());
        return serviceReferenceDAO.load(entryServiceId, startTime, endTime);
    }

    public JsonArray loadServiceTree(String entryServiceName, int entryApplicationId, long startTime, long endTime) {
        IServiceReferenceDAO serviceReferenceDAO = (IServiceReferenceDAO)DAOContainer.INSTANCE.get(IServiceReferenceDAO.class.getName());
        return serviceReferenceDAO.load(entryServiceName, entryApplicationId, startTime, endTime);
    }
}