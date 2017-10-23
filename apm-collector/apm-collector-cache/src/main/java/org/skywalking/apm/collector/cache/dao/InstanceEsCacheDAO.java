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

package org.skywalking.apm.collector.cache.dao;

import org.elasticsearch.action.get.GetResponse;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceEsCacheDAO extends EsDAO implements IInstanceCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceEsCacheDAO.class);

    @Override public int getApplicationId(int applicationInstanceId) {
        GetResponse response = getClient().prepareGet(InstanceTable.TABLE, String.valueOf(applicationInstanceId)).get();
        if (response.isExists()) {
            return (int)response.getSource().get(InstanceTable.COLUMN_APPLICATION_ID);
        } else {
            return 0;
        }
    }
}
