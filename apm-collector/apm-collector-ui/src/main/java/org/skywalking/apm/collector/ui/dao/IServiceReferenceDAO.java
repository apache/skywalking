package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;

/**
 * @author pengys5
 */
public interface IServiceReferenceDAO {
    JsonArray load(int entryServiceId, long startTime, long endTime);
}
