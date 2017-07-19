package org.skywalking.apm.collector.cluster.redis;

import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;

/**
 * @author pengys5
 */
public class ClusterRedisModuleRegistrationReader extends ClusterModuleRegistrationReader {

    public ClusterRedisModuleRegistrationReader(DataMonitor dataMonitor) {
        super(dataMonitor);
    }
}
