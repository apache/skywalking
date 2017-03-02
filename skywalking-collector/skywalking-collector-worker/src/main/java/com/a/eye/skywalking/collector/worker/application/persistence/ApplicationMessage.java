package com.a.eye.skywalking.collector.worker.application.persistence;

/**
 * @author pengys5
 */
public class ApplicationMessage {
    private final String code;
    private final String component;
    private final String host;
    private final String layer;

    public ApplicationMessage(String code, String component, String host, String layer) {
        this.code = code;
        this.component = component;
        this.host = host;
        this.layer = layer;
    }

    public String getCode() {
        return code;
    }

    public String getComponent() {
        return component;
    }

    public String getHost() {
        return host;
    }

    public String getLayer() {
        return layer;
    }
}
