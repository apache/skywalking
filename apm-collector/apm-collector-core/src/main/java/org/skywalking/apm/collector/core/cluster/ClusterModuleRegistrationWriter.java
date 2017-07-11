package org.skywalking.apm.collector.core.cluster;

/**
 * @author pengys5
 */
public interface ClusterModuleRegistrationWriter {
    void write(String key, String value);
}
