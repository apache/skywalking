package com.a.eye.skywalking.alarm.model;

public class ApplicationInfo {
    private String appId;
    private String configArgs;
    private String UId;
    private String toDoType;
    private String appCode;


    public ApplicationInfo() {
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getConfigArgs() {
        return configArgs;
    }

    public void setUId(String UId) {
        this.UId = UId;
    }

    public String getUId() {
        return UId;
    }

    public void setToDoType(String toDoType) {
        this.toDoType = toDoType;
    }

    public String getToDoType() {
        return toDoType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationInfo)) return false;

        ApplicationInfo that = (ApplicationInfo) o;

        if (getAppId() != null ? !getAppId().equals(that.getAppId()) : that.getAppId() != null) return false;
        return !(getUId() != null ? !getUId().equals(that.getUId()) : that.getUId() != null);

    }

    @Override
    public int hashCode() {
        int result = getAppId() != null ? getAppId().hashCode() : 0;
        result = 31 * result + (getUId() != null ? getUId().hashCode() : 0);
        return result;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppCode() {
        return appCode;
    }
}
