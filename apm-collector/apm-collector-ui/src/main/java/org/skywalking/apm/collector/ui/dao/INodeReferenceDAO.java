package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;

/**
 * @author pengys5
 */
public interface INodeReferenceDAO {
    JsonArray load(long startTime, long endTime);
}
