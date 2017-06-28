package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

/**
 * @author wusheng
 */
public interface Constructible {
    void call(Object[] args);
}
