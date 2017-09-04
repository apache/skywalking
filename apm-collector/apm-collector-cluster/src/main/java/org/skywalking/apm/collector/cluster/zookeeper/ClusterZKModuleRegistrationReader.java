package org.skywalking.apm.collector.cluster.zookeeper;

import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;

/**
 * @author pengys5
 */
public class ClusterZKModuleRegistrationReader extends ClusterModuleRegistrationReader {

    public ClusterZKModuleRegistrationReader(DataMonitor dataMonitor) {
        super(dataMonitor);
    }
}
