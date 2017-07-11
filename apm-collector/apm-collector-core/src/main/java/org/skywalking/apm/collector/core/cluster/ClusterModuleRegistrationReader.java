package org.skywalking.apm.collector.core.cluster;

import java.util.List;

/**
 * @author pengys5
 */
public interface ClusterModuleRegistrationReader {
    List<String> read(String key);
}
