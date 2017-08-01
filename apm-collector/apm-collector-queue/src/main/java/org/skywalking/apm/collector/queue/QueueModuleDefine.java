package org.skywalking.apm.collector.queue;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class QueueModuleDefine extends ModuleDefine {
    @Override protected final ModuleConfigParser configParser() {
        throw new UnsupportedOperationException("");
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        throw new UnsupportedOperationException("");
    }

    @Override protected final ModuleRegistration registration() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final Server server() {
        throw new UnsupportedOperationException("");
    }
}
