package org.skywalking.apm.collector.core.cluster;

/**
 * @author pengys5
 */
public class ClusterModuleContext {
    private ClusterModuleRegistrationWriter writer;

    public ClusterModuleRegistrationWriter getWriter() {
        return writer;
    }

    public void setWriter(ClusterModuleRegistrationWriter writer) {
        this.writer = writer;
    }
}
