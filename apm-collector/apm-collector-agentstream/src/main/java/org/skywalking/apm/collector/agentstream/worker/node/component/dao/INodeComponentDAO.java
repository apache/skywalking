package org.skywalking.apm.collector.agentstream.worker.node.component.dao;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public interface INodeComponentDAO {
    List<?> prepareBatch(Map<String, Data> dataMap);

    int getComponentId(int applicationId, String componentName);
}
