package org.skywalking.apm.collector.cluster;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.CollectorException;
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

    @Override public List<String> dependenceModules() {
        List<String> dependenceModules = new LinkedList<>();
        dependenceModules.add("collector_inside");
        return dependenceModules;
    }

    @Override public void onAfterInstall() throws CollectorException {
        ((ClusterModuleDefine)getModuleDefine()).startMonitor();
    }
}
