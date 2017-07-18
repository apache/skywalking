package org.skywalking.apm.collector.core.framework;

/**
 * @author pengys5
 */
public abstract class Context {
    private final String groupName;

    public Context(String groupName) {
        this.groupName = groupName;
    }

    public final String getGroupName() {
        return groupName;
    }
}
