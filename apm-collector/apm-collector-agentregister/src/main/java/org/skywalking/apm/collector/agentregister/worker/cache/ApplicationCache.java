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

package org.skywalking.apm.collector.agentregister.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.agentregister.worker.application.dao.IApplicationDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ApplicationCache {

    private static Cache<String, Integer> CACHE = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();

    public static int get(String applicationCode) {
        int applicationId = 0;
        try {
            applicationId = CACHE.get(applicationCode, () -> {
                IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
                return dao.getApplicationId(applicationCode);
            });
        } catch (Throwable e) {
            return applicationId;
        }

        if (applicationId == 0) {
            IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
            applicationId = dao.getApplicationId(applicationCode);
            if (applicationId != 0) {
                CACHE.put(applicationCode, applicationId);
            }
        }
        return applicationId;
    }
}
