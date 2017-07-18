package org.skywalking.apm.collector.core.cluster;

import org.skywalking.apm.collector.core.framework.Context;

/**
 * @author pengys5
 */
public class ClusterModuleContext extends Context {

    public ClusterModuleContext(String groupName) {
        super(groupName);
    }

    private ClusterModuleRegistrationWriter writer;

    private ClusterModuleRegistrationReader reader;

    public ClusterModuleRegistrationWriter getWriter() {
        return writer;
    }

    public void setWriter(ClusterModuleRegistrationWriter writer) {
        this.writer = writer;
    }

    public ClusterModuleRegistrationReader getReader() {
        return reader;
    }

    public void setReader(ClusterModuleRegistrationReader reader) {
        this.reader = reader;
    }
}
