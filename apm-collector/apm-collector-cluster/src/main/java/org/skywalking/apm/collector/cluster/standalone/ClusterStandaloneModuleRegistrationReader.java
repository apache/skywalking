package org.skywalking.apm.collector.cluster.standalone;

import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;

/**
 * @author pengys5
 */
public class ClusterStandaloneModuleRegistrationReader extends ClusterModuleRegistrationReader {

    public ClusterStandaloneModuleRegistrationReader(DataMonitor dataMonitor) {
        super(dataMonitor);
    }
}
