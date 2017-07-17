package org.skywalking.apm.collector.cluster.zookeeper;

import java.util.List;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;

/**
 * @author pengys5
 */
public class ClusterZKModuleRegistrationReader implements ClusterModuleRegistrationReader {
    @Override public List<String> read(String key) {
        return null;
    }
}
