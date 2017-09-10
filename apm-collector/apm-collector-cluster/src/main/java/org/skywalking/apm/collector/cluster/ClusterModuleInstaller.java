package org.skywalking.apm.collector.cluster;

import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.SingleModuleInstaller;

/**
 * @author pengys5
 */
public class ClusterModuleInstaller extends SingleModuleInstaller {

    @Override public String groupName() {
        return ClusterModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        ClusterModuleContext clusterModuleContext = new ClusterModuleContext(ClusterModuleGroupDefine.GROUP_NAME);
        CollectorContextHelper.INSTANCE.putClusterContext(clusterModuleContext);
        return clusterModuleContext;
    }
}
