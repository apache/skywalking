package com.a.eye.skywalking.plugin.dubbox;

import java.io.Serializable;

/**
 * All the request parameter of dubbox service need to extend {@link SWBaseBean} to transport
 * the serialized context data to the provider side if the version of dubbox is below 2.8.3.
 *
 * @author zhangxin
 */
public class SWBaseBean implements Serializable {
    /**
     * Serialized context data
     */
    private String contextData;

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }
}
