package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class NodeRefSumH2DAO extends H2DAO implements INodeRefSumDAO {
    @Override public JsonArray load(long startTime, long endTime) {
        return null;
    }
}
