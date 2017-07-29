package org.skywalking.apm.collector.agentstream.worker.noderef.summary.dao;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class NodeRefSumH2DAO extends H2DAO implements INodeRefSumDAO {

    @Override public List<?> prepareBatch(Map map) {
        return null;
    }
}
