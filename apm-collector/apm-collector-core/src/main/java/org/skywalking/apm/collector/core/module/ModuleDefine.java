package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DataInitializer;
import org.skywalking.apm.collector.core.framework.Define;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class ModuleDefine implements Define {

    protected abstract String group();

    public abstract boolean defaultModule();

    protected abstract ModuleConfigParser configParser();

    protected abstract Client createClient();

    protected abstract Server server();

    protected abstract DataInitializer dataInitializer();

    protected abstract ModuleRegistration registration();
}
