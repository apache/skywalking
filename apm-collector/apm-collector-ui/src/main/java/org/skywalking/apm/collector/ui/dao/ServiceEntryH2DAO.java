package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class ServiceEntryH2DAO extends H2DAO implements IServiceEntryDAO {

    @Override public JsonArray load(int applicationId, long startTime, long endTime) {
        return null;
    }
}
