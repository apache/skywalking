package org.skywalking.apm.collector.ui;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleModuleInstaller;

/**
 * @author pengys5
 */
public class UIModuleInstaller extends MultipleModuleInstaller {

    @Override public String groupName() {
        return UIModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new UIModuleContext(groupName());
    }
}
