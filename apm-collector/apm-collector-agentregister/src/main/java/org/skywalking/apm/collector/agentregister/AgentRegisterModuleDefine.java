package org.skywalking.apm.collector.agentregister;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.module.ModuleDefine;

/**
 * @author pengys5
 */
public abstract class AgentRegisterModuleDefine extends ModuleDefine implements ClusterDataListenerDefine {

    @Override protected void initializeOtherContext() {

    }

    @Override protected final Client createClient() {
        throw new UnsupportedOperationException("");
    }

    @Override public final boolean defaultModule() {
        return true;
    }
}
