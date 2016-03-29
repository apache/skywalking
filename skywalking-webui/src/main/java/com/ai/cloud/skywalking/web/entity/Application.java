package com.ai.cloud.skywalking.web.entity;

import com.ai.cloud.vo.svo.ConfigArgs;

import java.sql.Timestamp;

/**
 * Created by xin on 16-3-28.
 */
public class Application {
    private String appId;
    private String UId;
    private String appCode;
    private String sts;
    private Timestamp updateTime;
    private Timestamp createTime;

    private ConfigArgs configArgs;
    private String appDesc;

    public Application(String applicationId) {
        this.appId = applicationId;
    }

    public void setUId(String UId) {
        this.UId = UId;
    }

    public String getUId() {
        return UId;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setSts(String sts) {
        this.sts = sts;
    }

    public String getSts() {
        return sts;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Timestamp getCreateTime() {
        if (createTime == null){
            return new Timestamp(System.currentTimeMillis());
        }
        return createTime;
    }

    public ConfigArgs getConfigArgs() {
        return configArgs;
    }

    public String getAppId() {
        return appId;
    }

    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Timestamp getUpdateTime() {
        if (updateTime == null){
            return new Timestamp(System.currentTimeMillis());
        }
        return updateTime;
    }

    public String getAppDesc() {
        return appDesc;
    }

    public void setAppDesc(String appDesc) {
        this.appDesc = appDesc;
    }
}
