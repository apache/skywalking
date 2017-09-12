package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class ServiceReferenceH2DAO extends H2DAO implements IServiceReferenceDAO {

    @Override public JsonArray load(int entryServiceId, long startTime, long endTime) {
        return null;
    }

    @Override public JsonArray load(String entryServiceName, int entryApplicationId, long startTime, long endTime) {
        return null;
    }
}
