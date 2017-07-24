package org.skywalking.apm.collector.stream;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public class StreamModuleContext extends Context {

    private Map<Integer, DataDefine> dataDefineMap;
    private ClusterWorkerContext clusterWorkerContext;

    public StreamModuleContext(String groupName) {
        super(groupName);
        dataDefineMap = new HashMap<>();
    }

    public void putAllDataDefine(Map<Integer, DataDefine> dataDefineMap) {
        this.dataDefineMap.putAll(dataDefineMap);
    }

    public DataDefine getDataDefine(int dataDefineId) {
        return this.dataDefineMap.get(dataDefineId);
    }

    public ClusterWorkerContext getClusterWorkerContext() {
        return clusterWorkerContext;
    }

    public void setClusterWorkerContext(ClusterWorkerContext clusterWorkerContext) {
        this.clusterWorkerContext = clusterWorkerContext;
    }
}
