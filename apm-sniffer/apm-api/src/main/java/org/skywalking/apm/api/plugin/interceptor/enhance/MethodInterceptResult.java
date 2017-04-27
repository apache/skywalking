package org.skywalking.apm.api.plugin.interceptor.enhance;

/**
 * This is a method return value manipulator. When a interceptor's method, such as {@link
 * InstanceMethodsAroundInterceptor#beforeMethod(org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext, InstanceMethodInvokeContext,
 * MethodInterceptResult)}, has this as a method argument, the interceptor can manipulate the method's return value. <p>
 * The new value set to this object, by {@link MethodInterceptResult#defineReturnValue(Object)}, will override the
 * origin return value.
 *
 * @author wusheng
 */
public class MethodInterceptResult {
    private boolean isContinue = true;

    private Object ret = null;

    /**
     * define the new return value.
     *
     * @param ret new return value.
     */
    public void defineReturnValue(Object ret) {
        this.isContinue = false;
        this.ret = ret;
    }

    /**
     * @return true, will trigger method interceptor({@link ClassInstanceMethodsInterceptor} and {@link
     * ClassStaticMethodsInterceptor}) to invoke the origin method. Otherwise, not.
     */
    public boolean isContinue() {
        return isContinue;
    }

    /**
     * @return the new return value.
     */
    Object _ret() {
        return ret;
    }
}
