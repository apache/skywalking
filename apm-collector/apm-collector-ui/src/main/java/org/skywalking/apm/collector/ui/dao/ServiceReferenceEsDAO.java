package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceReferenceEsDAO extends EsDAO implements IServiceReferenceDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceEsDAO.class);

    @Override public JsonArray load(int entryServiceId, long startTime, long endTime) {
        return null;
    }
}
