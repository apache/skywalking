package org.skywalking.apm.collector.storage.elasticsearch;

import java.util.List;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
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
 * @author pengys5
 */
public class StorageElasticSearchModuleDefine extends StorageModuleDefine {

    public static final String MODULE_NAME = "elasticsearch";

    @Override protected String group() {
        return StorageModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StorageElasticSearchConfigParser();
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
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
