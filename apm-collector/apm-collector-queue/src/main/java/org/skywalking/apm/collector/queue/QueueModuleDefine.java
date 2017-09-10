package org.skywalking.apm.collector.queue;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class QueueModuleDefine extends ModuleDefine {

    @Override protected Client createClient() {
        return null;
    }

    @Override protected final ModuleRegistration registration() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final Server server() {
        return null;
    }

    @Override public final List<Handler> handlerList() {
        return null;
    }
}
