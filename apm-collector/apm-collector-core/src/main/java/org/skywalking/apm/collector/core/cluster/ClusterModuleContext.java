package org.skywalking.apm.collector.core.cluster;

import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.framework.Context;

/**
 * @author pengys5
 */
public class ClusterModuleContext extends Context {

    public ClusterModuleContext(String groupName) {
        super(groupName);
    }

    private ClusterModuleRegistrationReader reader;

    private DataMonitor dataMonitor;

    public ClusterModuleRegistrationReader getReader() {
        return reader;
    }

    public void setReader(ClusterModuleRegistrationReader reader) {
        this.reader = reader;
    }

    public DataMonitor getDataMonitor() {
        return dataMonitor;
    }

    public void setDataMonitor(DataMonitor dataMonitor) {
        this.dataMonitor = dataMonitor;
    }
}
