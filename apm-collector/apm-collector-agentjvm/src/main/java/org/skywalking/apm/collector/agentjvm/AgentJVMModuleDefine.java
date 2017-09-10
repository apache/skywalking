package org.skywalking.apm.collector.agentjvm;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.module.ModuleDefine;

/**
 * @author pengys5
 */
public abstract class AgentJVMModuleDefine extends ModuleDefine implements ClusterDataListenerDefine {

    @Override protected final Client createClient() {
        throw new UnsupportedOperationException("");
    }

    @Override protected void initializeOtherContext() {

    }

    @Override public final boolean defaultModule() {
        return true;
    }
}
