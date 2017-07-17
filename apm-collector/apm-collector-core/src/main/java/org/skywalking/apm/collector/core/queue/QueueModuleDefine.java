package org.skywalking.apm.collector.core.queue;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DataInitializer;
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

    @Override protected final Client createClient() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final DataInitializer dataInitializer() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final ModuleRegistration registration() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final Server server() {
        throw new UnsupportedOperationException("");
    }
}
