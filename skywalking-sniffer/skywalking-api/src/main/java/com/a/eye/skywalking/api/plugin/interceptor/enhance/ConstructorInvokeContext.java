package com.a.eye.skywalking.api.plugin.interceptor.enhance;

/**
 * Constructor context.
 *
 * @author wusheng
 */
public class ConstructorInvokeContext {
    /**
     * object instance
     */
    private Object objInst;
    /**
     * constructor's arguments list.
     */
    private Object[] allArguments;

    ConstructorInvokeContext(Object objInst, Object[] allArguments) {
        this.objInst = objInst;
        this.allArguments = allArguments;
    }

    /**
     * @return object instance
     */
    public Object inst() {
        return objInst;
    }

    /**
     * @return arguments list.
     */
    public Object[] allArguments() {
        return this.allArguments;
    }
}
