package org.skywalking.apm.collector.remote.grpc;

import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.remote.RemoteModuleGroupDefine;

/**
 * @author pengys5
 */
public class RemoteGRPCDataListener extends ClusterDataListener {

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + RemoteModuleGroupDefine.GROUP_NAME + "." + RemoteGRPCModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }
}
