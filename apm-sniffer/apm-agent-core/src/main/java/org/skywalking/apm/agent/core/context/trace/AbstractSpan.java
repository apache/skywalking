package org.skywalking.apm.agent.core.context.trace;

import java.util.Map;

/**
 * The <code>AbstractSpan</code> represents the span's skeleton,
 * which contains all open methods.
 *
 * @author wusheng
 */
public interface AbstractSpan {
    AbstractSpan setOperationName(String operationName);

    void setPeerHost(String peerHost);

    void setPort(int port);

    void setPeers(String peers);

    AbstractSpan setTag(String key, String value);

    AbstractSpan setTag(String key, boolean value);

    AbstractSpan setTag(String key, Integer value);

    AbstractSpan log(Map<String, String> fields);

    AbstractSpan log(Throwable t);

    AbstractSpan log(String event);
}
