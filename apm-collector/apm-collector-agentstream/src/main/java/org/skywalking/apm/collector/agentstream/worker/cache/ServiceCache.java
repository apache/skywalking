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

package org.skywalking.apm.collector.agentstream.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.agentregister.worker.servicename.dao.IServiceNameDAO;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ServiceCache {

    private static Cache<Integer, String> CACHE = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(20000).build();

    public static String getServiceName(int serviceId) {
        try {
            return CACHE.get(serviceId, () -> {
                IServiceNameDAO dao = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());
                return dao.getServiceName(serviceId);
            });
        } catch (Throwable e) {
            return Const.EMPTY_STRING;
        }
    }
}
