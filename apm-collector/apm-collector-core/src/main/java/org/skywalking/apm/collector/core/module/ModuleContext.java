package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;

/**
 * @author pengys5
 */
public class ModuleContext {
    private ClusterModuleContext clusterContext;

    public ClusterModuleContext getClusterContext() {
        return clusterContext;
    }

    public void setClusterContext(ClusterModuleContext clusterContext) {
        this.clusterContext = clusterContext;
    }
}
