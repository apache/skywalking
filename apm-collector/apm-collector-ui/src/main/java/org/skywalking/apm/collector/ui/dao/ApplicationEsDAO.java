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

package org.skywalking.apm.collector.ui.dao;

import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.register.ApplicationTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ApplicationEsDAO extends EsDAO implements IApplicationDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationEsDAO.class);

    @Override public String getApplicationCode(int applicationId) {
        logger.debug("get application code, applicationId: {}", applicationId);
        ElasticSearchClient client = getClient();
        GetRequestBuilder getRequestBuilder = client.prepareGet(ApplicationTable.TABLE, String.valueOf(applicationId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            return (String)getResponse.getSource().get(ApplicationTable.COLUMN_APPLICATION_CODE);
        }
        return Const.UNKNOWN;
    }
}
