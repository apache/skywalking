package org.skywalking.apm.collector.core.module;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.framework.Define;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class ModuleDefine implements Define {

    protected abstract String group();

    public abstract boolean defaultModule();

    protected abstract ModuleConfigParser configParser();

    protected abstract Client createClient(DataMonitor dataMonitor);

    protected abstract Server server();

    public abstract List<Handler> handlerList();

    protected abstract ModuleRegistration registration();

    protected abstract void initializeOtherContext();
}
