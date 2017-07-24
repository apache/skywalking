package org.skywalking.apm.collector.stream.grpc;

import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;

/**
 * @author pengys5
 */
public class StreamGRPCDataListener extends ClusterDataListener {

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + StreamModuleGroupDefine.GROUP_NAME + "." + StreamGRPCModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }
}
