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

package org.skywalking.apm.collector.agentregister.worker.application.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.storage.define.register.ApplicationTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationEsDAO extends EsDAO implements IApplicationDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationEsDAO.class);

    @Override public int getMaxApplicationId() {
        return getMaxId(ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID);
    }

    @Override public int getMinApplicationId() {
        return getMinId(ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID);
    }

    @Override public void save(ApplicationDataDefine.Application application) {
        logger.debug("save application register info, application id: {}, application code: {}", application.getApplicationId(), application.getApplicationCode());
        ElasticSearchClient client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationTable.COLUMN_APPLICATION_CODE, application.getApplicationCode());
        source.put(ApplicationTable.COLUMN_APPLICATION_ID, application.getApplicationId());

        IndexResponse response = client.prepareIndex(ApplicationTable.TABLE, application.getId()).setSource(source).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
        logger.debug("save application register info, application id: {}, application code: {}, status: {}", application.getApplicationId(), application.getApplicationCode(), response.status().name());
    }
}
