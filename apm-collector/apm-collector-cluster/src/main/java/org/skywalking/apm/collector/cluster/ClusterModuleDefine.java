package org.skywalking.apm.collector.cluster;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public abstract class ClusterModuleDefine extends ModuleDefine {

    public static final String BASE_CATALOG = "skywalking";

    private Client client;
    private DataMonitor dataMonitor;

    @Override protected void initializeOtherContext() {
        try {
            dataMonitor = dataMonitor();
            client = createClient(dataMonitor);
            client.initialize();
            dataMonitor.setClient(client);
            ClusterModuleRegistrationReader reader = registrationReader(dataMonitor);

            CollectorContextHelper.INSTANCE.getClusterModuleContext().setDataMonitor(dataMonitor);
            CollectorContextHelper.INSTANCE.getClusterModuleContext().setReader(reader);
        } catch (ClientException e) {
            throw new UnexpectedException(e.getMessage());
        }
    }

    public final Client getClient() {
        return this.client;
    }

    @Override public final Server server() {
        throw new UnsupportedOperationException("");
    }

    @Override public final List<Handler> handlerList() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final ModuleRegistration registration() {
        throw new UnsupportedOperationException("Cluster module do not need module registration.");
    }

    public abstract DataMonitor dataMonitor();

    public abstract ClusterModuleRegistrationReader registrationReader(DataMonitor dataMonitor);

}
