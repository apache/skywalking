package com.a.eye.skywalking.registry.api;

/**
 * Created by xin on 2016/11/10.
 */
public enum CenterType {
    zookeeper("zookeeper");

    private String type;

    CenterType(String typeStr) {
        this.type = typeStr;
    }


    public String getType() {
        return type;
    }
}
