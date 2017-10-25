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

package org.skywalking.apm.collector.storage.elasticsearch;

import java.util.List;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.storage.StorageInstaller;
import org.skywalking.apm.collector.storage.StorageModuleDefine;
import org.skywalking.apm.collector.storage.StorageModuleGroupDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAODefineLoader;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchStorageInstaller;

/**
 * @author peng-yongsheng
 */
public class StorageElasticSearchModuleDefine extends StorageModuleDefine {

    public static final String MODULE_NAME = "elasticsearch";

    @Override protected String group() {
        return StorageModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public final boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StorageElasticSearchConfigParser();
    }

    @Override protected Client createClient() {
        return new ElasticSearchClient(StorageElasticSearchConfig.CLUSTER_NAME, StorageElasticSearchConfig.CLUSTER_TRANSPORT_SNIFFER, StorageElasticSearchConfig.CLUSTER_NODES);
    }

    @Override public StorageInstaller storageInstaller() {
        return new ElasticSearchStorageInstaller();
    }

    @Override public void injectClientIntoDAO(Client client) throws DefineException {
        EsDAODefineLoader loader = new EsDAODefineLoader();
        List<EsDAO> esDAOs = loader.load();
        esDAOs.forEach(esDAO -> {
            esDAO.setClient((ElasticSearchClient)client);
            String interFaceName = esDAO.getClass().getInterfaces()[0].getName();
            DAOContainer.INSTANCE.put(interFaceName, esDAO);
        });
    }
}
