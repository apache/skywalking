package org.skywalking.apm.collector.stream;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class StreamModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "stream";

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new StreamModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return new StreamModuleInstaller();
    }
}
