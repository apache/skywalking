package org.skywalking.apm.collector.cluster;

import java.util.Map;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.cluster.ClusterModuleException;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class ClusterModuleDefine extends ModuleDefine {

    public static final String BASE_CATALOG = "skywalking";

    private Client client;

    @Override public final void initialize(Map config) throws ClusterModuleException {
        try {
            configParser().parse(config);

            DataMonitor dataMonitor = dataMonitor();
            client = createClient(dataMonitor);
            client.initialize();
            dataMonitor.setClient(client);

            ((ClusterModuleContext)CollectorContextHelper.INSTANCE.getContext(group())).setDataMonitor(dataMonitor);
        } catch (ConfigParseException | ClientException e) {
            throw new ClusterModuleException(e.getMessage(), e);
        }
    }

    public final Client getClient() {
        return this.client;
    }

    @Override public final Server server() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final ModuleRegistration registration() {
        throw new UnsupportedOperationException("Cluster module do not need module registration.");
    }

    public abstract DataMonitor dataMonitor();

    public abstract ClusterModuleRegistrationReader registrationReader();
}
