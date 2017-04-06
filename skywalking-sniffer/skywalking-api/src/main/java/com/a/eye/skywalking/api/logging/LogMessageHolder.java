package com.a.eye.skywalking.api.logging;

/**
 * The <code>LogMessageHolder</code> is a {@link String} holder,
 * in order to in-process propagation String across the disruptor queue.
 *
 * @author wusheng
 */
public class LogMessageHolder {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
