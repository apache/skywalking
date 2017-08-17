package org.skywalking.apm.collector.stream;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class StreamModuleDefine extends ModuleDefine implements ClusterDataListenerDefine {

    private final Logger logger = LoggerFactory.getLogger(StreamModuleDefine.class);

    @Override
    public final void initialize(Map config, ServerHolder serverHolder) throws DefineException, ClientException {
        try {
            configParser().parse(config);
            Server server = server();
            serverHolder.holdServer(server, handlerList());

            ((ClusterModuleContext)CollectorContextHelper.INSTANCE.getContext(ClusterModuleGroupDefine.GROUP_NAME)).getDataMonitor().addListener(listener(), registration());
        } catch (ConfigParseException | ServerException e) {
            throw new StreamModuleException(e.getMessage(), e);
        }
    }

    @Override public final boolean defaultModule() {
        return true;
    }

    public abstract List<Handler> handlerList() throws DefineException;
}
