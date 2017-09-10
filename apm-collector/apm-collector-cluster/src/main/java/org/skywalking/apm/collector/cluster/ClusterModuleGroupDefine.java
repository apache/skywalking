package org.skywalking.apm.collector.cluster;

import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class ClusterModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "cluster";
    private final ClusterModuleInstaller installer;

    public ClusterModuleGroupDefine() {
        installer = new ClusterModuleInstaller();
    }

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new ClusterModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return installer;
    }
}
