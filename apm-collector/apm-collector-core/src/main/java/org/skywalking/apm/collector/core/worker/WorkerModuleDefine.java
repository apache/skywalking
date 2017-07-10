package org.skywalking.apm.collector.core.worker;

import java.util.Map;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.framework.DataInitializer;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleException;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.server.ServerException;

/**
 * @author pengys5
 */
public abstract class WorkerModuleDefine extends ModuleDefine {

    @Override public final void initialize(Map config) throws ModuleException {
        try {
            configParser().parse(config);
            Server server = server();
            server.initialize();
        } catch (ConfigParseException | ServerException e) {
            throw new WorkerModuleException(e.getMessage(), e);
        }
    }

    @Override public final Client client() {
        throw new UnsupportedOperationException();
    }

    @Override public final DataInitializer dataInitializer() {
        throw new UnsupportedOperationException();
    }
}
