package org.skywalking.apm.collector.cluster.redis;

import java.util.List;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;

/**
 * @author pengys5
 */
public class ClusterRedisModuleRegistrationReader implements ClusterModuleRegistrationReader {
    @Override public List<String> read(String key) {
        return null;
    }
}
