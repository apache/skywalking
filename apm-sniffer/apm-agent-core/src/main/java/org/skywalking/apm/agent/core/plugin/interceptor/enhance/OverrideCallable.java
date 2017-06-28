package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

/**
 * @author wusheng
 */
public interface OverrideCallable {
    Object call(Object[] args);
}
