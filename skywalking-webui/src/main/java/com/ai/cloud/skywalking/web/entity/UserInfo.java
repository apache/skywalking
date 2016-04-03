package com.ai.cloud.skywalking.web.entity;

import java.sql.Timestamp;

/**
 * Created by xin on 16-3-25.
 */
public class UserInfo {
    private String uid;
    private String userName;
    private String password;
    private String roleType;
    private Timestamp createTime;
    private String sts;
    private Timestamp modifyTime;

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getUid() {
        return uid;
    }

    public String getRoleType() {
        return roleType;
    }

    public String getSts() {
        return sts;
    }

    public Timestamp getCreateTime() {
        if (createTime == null){
            return new Timestamp(System.currentTimeMillis());
        }
        return createTime;
    }

    public Timestamp getModifyTime() {
        if (modifyTime == null){
            return new Timestamp(System.currentTimeMillis());
        }
        return modifyTime;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public void setSts(String sts) {
        this.sts = sts;
    }


}
