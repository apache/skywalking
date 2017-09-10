package org.skywalking.apm.collector.ui;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.module.ModuleDefine;

/**
 * @author pengys5
 */
public abstract class UIModuleDefine extends ModuleDefine implements ClusterDataListenerDefine {

    @Override protected final Client createClient(DataMonitor dataMonitor) {
        throw new UnsupportedOperationException("");
    }

    @Override public final boolean defaultModule() {
        return true;
    }

    @Override protected final void initializeOtherContext() {

    }
}
