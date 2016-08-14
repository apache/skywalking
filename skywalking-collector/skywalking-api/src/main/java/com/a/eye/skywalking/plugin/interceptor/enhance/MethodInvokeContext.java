package com.a.eye.skywalking.plugin.interceptor.enhance;

/**
 * 方法执行拦截上下文
 *
 * @author wusheng
 */
public class MethodInvokeContext {
    /**
     * 方法名称
     */
    private String   methodName;
    /**
     * 方法参数
     */
    private Object[] allArguments;

    private Class<?>[] argumentTypes;

    MethodInvokeContext(String methodName, Object[] allArguments,Class<?>[] argumentTypes) {
        this.methodName = methodName;
        this.allArguments = allArguments;
        this.argumentTypes = argumentTypes;
    }

    public Object[] allArguments() {
        return this.allArguments;
    }

    public String methodName() {
        return methodName;
    }

    public Class<?>[] argumentTypes(){
        return argumentTypes;
    }

}
