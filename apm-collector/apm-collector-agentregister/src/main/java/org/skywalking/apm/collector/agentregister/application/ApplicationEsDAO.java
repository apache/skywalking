package org.skywalking.apm.collector.agentregister.application;

import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author pengys5
 */
public class ApplicationEsDAO extends EsDAO implements IApplicationDAO {

    @Override public int getApplicationId(String applicationCode) {
        ElasticSearchClient client = getClient();
        return 0;
    }
}
