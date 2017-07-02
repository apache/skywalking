package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

/**
 * @author wusheng
 */
public interface EnhancedInstance {
    Object getSkyWalkingDynamicField();

    void setSkyWalkingDynamicField();
}
