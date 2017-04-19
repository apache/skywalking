package com.a.eye.skywalking.plugin.mongodb.v3;

/**
 * {@link OperationInfo} record the methodName and filter information
 *
 * @author baiyang
 */
public class OperationInfo {

    private String methodName;

    private String filter;

    public OperationInfo() {

    }

    public OperationInfo(String methodName) {
        super();
        this.methodName = methodName;
        this.filter = "";
    }

    public OperationInfo(String methodName, String filter) {
        super();
        this.methodName = methodName;
        this.filter = filter;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @Override
    public String toString() {
        return "{methodName=" + methodName + ", filter=" + filter + "}";
    }

}
