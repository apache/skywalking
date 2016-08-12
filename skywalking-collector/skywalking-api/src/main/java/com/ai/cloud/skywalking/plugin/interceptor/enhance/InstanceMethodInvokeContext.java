package com.ai.cloud.skywalking.plugin.interceptor.enhance;

public class InstanceMethodInvokeContext extends MethodInvokeContext {
    /**
     * 代理类实例
     */
    private Object objInst;

    InstanceMethodInvokeContext(Object objInst, String methodName, Object[] allArguments, Class<?>[] argumentsTypes) {
        super(methodName, allArguments,argumentsTypes);
        this.objInst = objInst;
    }

    public Object inst() {
        return objInst;
    }
}
