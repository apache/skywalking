package org.skywalking.apm.plugin.dubbox;

import java.io.Serializable;

/**
 * All the request parameter of dubbox service need to extend {@link SWBaseBean} to transport
 * the serialized trace context to the provider side if the version of dubbox is below 2.8.3.
 *
 * @author zhangxin
 */
public class SWBaseBean implements Serializable {
    /**
     * Serialized trace context.
     */
    private String traceContext;

    public String getTraceContext() {
        return traceContext;
    }

    public void setTraceContext(String traceContext) {
        this.traceContext = traceContext;
    }
}
