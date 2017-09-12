package org.skywalking.apm.collector.queue;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class QueueModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "queue";
    private final QueueModuleInstaller installer;

    public QueueModuleGroupDefine() {
        installer = new QueueModuleInstaller();
    }

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new QueueModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return installer;
    }
}
