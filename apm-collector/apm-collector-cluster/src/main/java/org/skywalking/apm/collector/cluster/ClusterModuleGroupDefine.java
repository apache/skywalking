package org.skywalking.apm.collector.cluster;

import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstallMode;

/**
 * @author pengys5
 */
public class ClusterModuleGroupDefine implements ModuleGroupDefine {
    @Override public String name() {
        return "cluster";
    }

    @Override public ModuleInstallMode mode() {
        return ModuleInstallMode.Single;
    }
}
