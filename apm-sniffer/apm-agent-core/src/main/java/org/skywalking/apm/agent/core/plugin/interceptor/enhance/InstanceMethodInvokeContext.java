package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

/**
 * Instance method invoke context.
 * Beside all in {@link MethodInvokeContext}, plus Object instance ref.
 *
 * @author wusheng
 */
public class InstanceMethodInvokeContext extends MethodInvokeContext {
    private Object objInst;

    InstanceMethodInvokeContext(Object objInst, String methodName, Object[] allArguments, Class<?>[] argumentsTypes) {
        super(methodName, allArguments, argumentsTypes);
        this.objInst = objInst;
    }

    /**
     * @return the target instance's ref.
     */
    public Object inst() {
        return objInst;
    }
}
