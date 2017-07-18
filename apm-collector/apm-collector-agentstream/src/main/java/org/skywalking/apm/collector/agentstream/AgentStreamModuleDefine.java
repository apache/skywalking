package org.skywalking.apm.collector.agentstream;

import java.util.Map;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.server.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class AgentStreamModuleDefine extends ModuleDefine implements ClusterDataListenerDefine {

    private final Logger logger = LoggerFactory.getLogger(AgentStreamModuleDefine.class);

    @Override public final void initialize(Map config) throws DefineException, ClientException {
        try {
            configParser().parse(config);
            Server server = server();
            server.initialize();

            ((ClusterModuleContext)CollectorContextHelper.INSTANCE.getContext(ClusterModuleGroupDefine.GROUP_NAME)).getDataMonitor().addListener(listener(), registration());
        } catch (ConfigParseException | ServerException e) {
            throw new AgentStreamModuleException(e.getMessage(), e);
        }
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        throw new UnsupportedOperationException("");
    }

    @Override public final boolean defaultModule() {
        return true;
    }
}
