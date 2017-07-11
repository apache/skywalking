package org.skywalking.apm.collector.core.cluster;

import java.util.Map;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class ClusterModuleDefine extends ModuleDefine {

    @Override public final void initialize(Map config) throws ClusterModuleException {
        try {
            configParser().parse(config);
            Client client = client();
            client.initialize();
            dataInitializer().initialize(client);
        } catch (ConfigParseException | ClientException e) {
            throw new ClusterModuleException(e.getMessage(), e);
        }
    }

    @Override public final Server server() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final ModuleRegistration registration() {
        throw new UnsupportedOperationException("Cluster module do not need module registration.");
    }

    protected abstract ClusterModuleRegistrationWriter registrationWriter();
}
