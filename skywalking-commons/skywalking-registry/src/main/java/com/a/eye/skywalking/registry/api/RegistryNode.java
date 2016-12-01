package com.a.eye.skywalking.registry.api;

/**
 * Created by xin on 2016/12/1.
 */
public class RegistryNode {

    private String node;
    private ChangeType changeType;

    public RegistryNode(String node, ChangeType eventType) {
        this.node = node;
        this.changeType = eventType;
    }

    public enum ChangeType {
        ADDED, REMOVED
    }

    public String getNode() {
        return node;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}
