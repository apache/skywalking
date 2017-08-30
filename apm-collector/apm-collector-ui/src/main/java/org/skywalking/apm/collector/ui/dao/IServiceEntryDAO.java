package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public interface IServiceEntryDAO {
    JsonObject load(int applicationId, String entryServiceName, long startTime, long endTime, int from, int size);
}
