package org.skywalking.apm.collector.ui;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class UIModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "ui";

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new UIModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return new UIModuleInstaller();
    }
}
