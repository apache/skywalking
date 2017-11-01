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

package org.skywalking.apm.collector.storage.es;

import java.util.List;
import java.util.Properties;
import org.skywalking.apm.collector.client.ClientException;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.define.DefineException;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.storage.StorageException;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.base.dao.DAOContainer;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAODefineLoader;
import org.skywalking.apm.collector.storage.es.base.define.ElasticSearchStorageInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StorageModuleEsProvider extends ModuleProvider {

    private final Logger logger = LoggerFactory.getLogger(StorageModuleEsProvider.class);

    private static final String CLUSTER_NAME = "cluster_name";
    private static final String CLUSTER_TRANSPORT_SNIFFER = "cluster_transport_sniffer";
    private static final String CLUSTER_NODES = "cluster_nodes";
    private static final String INDEX_SHARDS_NUMBER = "index_shards_number";
    private static final String INDEX_REPLICAS_NUMBER = "index_replicas_number";

    private ElasticSearchClient elasticSearchClient;

    @Override public String name() {
        return "elasticsearch";
    }

    @Override public Class<? extends Module> module() {
        return StorageModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        String clusterName = config.getProperty(CLUSTER_NAME);
        Boolean clusterTransportSniffer = (Boolean)config.get(CLUSTER_TRANSPORT_SNIFFER);
        String clusterNodes = config.getProperty(CLUSTER_NODES);
        elasticSearchClient = new ElasticSearchClient(clusterName, clusterTransportSniffer, clusterNodes);
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        Integer indexShardsNumber = (Integer)config.get(INDEX_SHARDS_NUMBER);
        Integer indexReplicasNumber = (Integer)config.get(INDEX_REPLICAS_NUMBER);
        try {
            elasticSearchClient.initialize();

            EsDAODefineLoader loader = new EsDAODefineLoader();
            List<EsDAO> esDAOs = loader.load();
            esDAOs.forEach(esDAO -> {
                esDAO.setClient(elasticSearchClient);
                String interFaceName = esDAO.getClass().getInterfaces()[0].getName();
                DAOContainer.INSTANCE.put(interFaceName, esDAO);
            });

            ElasticSearchStorageInstaller installer = new ElasticSearchStorageInstaller(indexShardsNumber, indexReplicasNumber);
            installer.install(elasticSearchClient);
        } catch (ClientException | DefineException | StorageException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
