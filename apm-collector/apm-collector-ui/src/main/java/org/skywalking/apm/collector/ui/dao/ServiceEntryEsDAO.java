package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author pengys5
 */
public class ServiceEntryEsDAO extends EsDAO implements IServiceEntryDAO {

    @Override public JsonArray load(int applicationId, long startTime, long endTime) {
        return null;
    }
}
