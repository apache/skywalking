package org.skywalking.apm.collector.storage;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class StorageModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "storage";
    private final StorageModuleInstaller installer;

    public StorageModuleGroupDefine() {
        installer = new StorageModuleInstaller();
    }

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new StorageModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return installer;
    }
}
