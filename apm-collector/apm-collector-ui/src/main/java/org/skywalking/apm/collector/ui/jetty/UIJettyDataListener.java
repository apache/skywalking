package org.skywalking.apm.collector.ui.jetty;

import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.ui.UIModuleGroupDefine;

/**
 * @author pengys5
 */
public class UIJettyDataListener extends ClusterDataListener {

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + UIModuleGroupDefine.GROUP_NAME + "." + UIJettyModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }

    @Override public void serverJoinNotify(String serverAddress) {

    }

    @Override public void serverQuitNotify(String serverAddress) {
    }
}
