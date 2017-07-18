package org.skywalking.apm.collector.remote;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class RemoteModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "remote";

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new RemoteModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return new RemoteModuleInstaller();
    }
}
