package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;

/**
 * @author pengys5
 */
public interface IServiceEntryDAO {
    JsonArray load(int applicationId, long startTime, long endTime);
}
