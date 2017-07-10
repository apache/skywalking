package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DataInitializer;
import org.skywalking.apm.collector.core.framework.Define;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class ModuleDefine implements Define {

    private String moduleName;

    @Override public final String getName() {
        return moduleName;
    }

    @Override public final void setName(String name) {
        this.moduleName = name;
    }

    protected abstract ModuleGroup group();

    protected abstract boolean defaultModule();

    protected abstract ModuleConfigParser configParser();

    protected abstract Client client();

    protected abstract Server server();

    protected abstract DataInitializer dataInitializer();
}
