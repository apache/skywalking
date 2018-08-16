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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache;

import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.get.GetResponse;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceInventoryCacheEsDAO extends EsDAO implements IServiceInventoryCacheDAO {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInventoryCacheEsDAO.class);

    public ServiceInventoryCacheEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int get(String id) {
        try {
            GetResponse response = getClient().get(ServiceInventory.MODEL_NAME, id);
            if (response.isExists()) {
                return response.getField(RegisterSource.SEQUENCE).getValue();
            } else {
                return 0;
            }
        } catch (Throwable e) {
            logger.error(e.getMessage());
            return 0;
        }
    }

    @Override public ServiceInventory get(int sequence) {
        return null;
    }

    @Override public int getServiceIdByAddressId(int addressId) {
        return 0;
    }
}
