package org.skywalking.apm.collector.core.cluster;

import java.util.Set;
import org.skywalking.apm.collector.core.client.DataMonitor;

/**
 * @author pengys5
 */
public abstract class ClusterModuleRegistrationReader {

    private final DataMonitor dataMonitor;

    public ClusterModuleRegistrationReader(DataMonitor dataMonitor) {
        this.dataMonitor = dataMonitor;
    }

    public final Set<String> read(String path) {
        return dataMonitor.getListener(path).getAddresses();
    }
}
