package org.skywalking.apm.collector.storage.h2;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.storage.StorageModuleDefine;
import org.skywalking.apm.collector.storage.StorageModuleGroupDefine;

/**
 * @author pengys5
 */
public class StorageH2ModuleDefine extends StorageModuleDefine {

    public static final String MODULE_NAME = "h2";

    @Override protected String group() {
        return StorageModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StorageH2ConfigParser();
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        return new H2Client();
    }
}
