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

package org.skywalking.apm.collector.ui.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IApplicationDAO;

/**
 * @author pengys5
 */
public class ApplicationCache {

    //TODO size configuration
    private static Cache<Integer, String> CACHE = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static String get(int applicationId) {
        try {
            return CACHE.get(applicationId, () -> {
                IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
                return dao.getApplicationCode(applicationId);
            });
        } catch (Throwable e) {
            return Const.EXCEPTION;
        }
    }

    public static String getForUI(int applicationId) {
        String applicationCode = get(applicationId);
        if (applicationCode.equals("Unknown")) {
            IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
            applicationCode = dao.getApplicationCode(applicationId);
            CACHE.put(applicationId, applicationCode);
        }
        return applicationCode;
    }
}
