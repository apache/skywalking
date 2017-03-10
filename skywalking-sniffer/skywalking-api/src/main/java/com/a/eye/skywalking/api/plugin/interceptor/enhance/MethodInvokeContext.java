package com.a.eye.skywalking.api.plugin.interceptor.enhance;

/**
 * Method invoke context.
 * Include method name, arguments list and argument types list.
 *
 * @author wusheng
 */
public class MethodInvokeContext {
    private String   methodName;
    private Object[] allArguments;
    private Class<?>[] argumentTypes;

    MethodInvokeContext(String methodName, Object[] allArguments,Class<?>[] argumentTypes) {
        this.methodName = methodName;
        this.allArguments = allArguments;
        this.argumentTypes = argumentTypes;
    }

    /**
     * @return arguments list.
     */
    public Object[] allArguments() {
        return this.allArguments;
    }

    /**
     * @return method name.
     */
    public String methodName() {
        return methodName;
    }

    /**
     * @return argument types list.
     */
    public Class<?>[] argumentTypes(){
        return argumentTypes;
    }

}
