package org.skywalking.apm.collector.storage;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.SingleModuleInstaller;

/**
 * @author pengys5
 */
public class StorageModuleInstaller extends SingleModuleInstaller {

    @Override public String groupName() {
        return StorageModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new StorageModuleContext(groupName());
    }
}
