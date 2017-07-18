package org.skywalking.apm.collector.remote;

import org.skywalking.apm.collector.core.framework.Context;

/**
 * @author pengys5
 */
public class RemoteModuleContext extends Context {
    public RemoteModuleContext(String groupName) {
        super(groupName);
    }
}
