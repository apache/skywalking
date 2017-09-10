package org.skywalking.apm.collector.stream;

import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.module.ModuleDefine;

/**
 * @author pengys5
 */
public abstract class StreamModuleDefine extends ModuleDefine implements ClusterDataListenerDefine {

    @Override public final boolean defaultModule() {
        return true;
    }

    @Override protected final void initializeOtherContext() {

    }
}
