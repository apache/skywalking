package org.skywalking.apm.collector.core.agentstream;

import java.util.Map;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataInitializer;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.framework.DataInitializer;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.server.ServerException;

/**
 * @author pengys5
 */
public abstract class AgentStreamModuleDefine extends ModuleDefine {

    @Override public final void initialize(Map config) throws DefineException, ClientException {
        try {
            configParser().parse(config);
            Server server = server();
            server.initialize();

            String key = ClusterDataInitializer.BASE_CATALOG + "." + name();
            ClusterModuleContext.WRITER.write(key, registration().buildValue());
        } catch (ConfigParseException | ServerException e) {
            throw new AgentStreamModuleException(e.getMessage(), e);
        }
    }

    @Override protected final DataInitializer dataInitializer() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final Client createClient() {
        throw new UnsupportedOperationException("");
    }
}
