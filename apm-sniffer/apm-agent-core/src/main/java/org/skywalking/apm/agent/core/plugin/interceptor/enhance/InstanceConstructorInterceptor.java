package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

/**
 * The instance constructor's interceptor interface.
 * Any plugin, which wants to intercept constructor, must implement this interface.
 * <p>
 *
 * @author wusheng
 */
public interface InstanceConstructorInterceptor {
    /**
     * Called before the origin constructor invocation.
     */
    void onConstruct(Object objInst, Object[] allArguments);
}
