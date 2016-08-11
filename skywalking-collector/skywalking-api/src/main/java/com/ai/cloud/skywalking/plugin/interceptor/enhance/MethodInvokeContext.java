package com.ai.cloud.skywalking.plugin.interceptor.enhance;

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

    MethodInvokeContext(Class clazz, String methodName, Object[] allArguments) {
        this.methodName = appendMethodName(clazz, methodName, allArguments);
        this.allArguments = allArguments;
    }

    public Object[] allArguments() {
        return this.allArguments;
    }

    public String methodName() {
        return methodName;
    }

    private String appendMethodName(Class clazz, String simpleMethodName, Object[] allArguments) {
        StringBuilder methodName = new StringBuilder(clazz.getName() + "." + simpleMethodName + "(");
        for (Object argument : allArguments) {
            methodName.append(argument.getClass() + ",");
        }

        if (allArguments.length > 0){
            methodName.deleteCharAt(methodName.length() - 1);
        }

        methodName.append(")");
        return methodName.toString();
    }
}
