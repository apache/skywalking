package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class ServiceEntryH2DAO extends H2DAO implements IServiceEntryDAO {
    @Override public JsonObject load(int applicationId, String entryServiceName, long startTime, long endTime, int from,
        int size) {
        return null;
    }
}
