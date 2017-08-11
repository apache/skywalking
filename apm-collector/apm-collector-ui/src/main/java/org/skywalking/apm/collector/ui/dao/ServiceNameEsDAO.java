package org.skywalking.apm.collector.ui.dao;

import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.register.ServiceNameTable;

/**
 * @author pengys5
 */
public class ServiceNameEsDAO extends EsDAO implements IServiceNameDAO {

    @Override public String getServiceName(int serviceId) {
        ElasticSearchClient client = getClient();
        GetRequestBuilder getRequestBuilder = client.prepareGet(ServiceNameTable.TABLE, String.valueOf(serviceId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            return (String)getResponse.getSource().get(ServiceNameTable.COLUMN_SERVICE_NAME);
        }
        return Const.UNKNOWN;
    }
}
