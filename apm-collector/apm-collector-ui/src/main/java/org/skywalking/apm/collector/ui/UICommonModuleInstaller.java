package org.skywalking.apm.collector.ui;

import java.util.List;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleCommonModuleInstaller;

/**
 * @author pengys5
 */
public class UICommonModuleInstaller extends MultipleCommonModuleInstaller {

    @Override public String groupName() {
        return UIModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new UIModuleContext(groupName());
    }

    @Override public List<String> dependenceModules() {
        return null;
    }
}
