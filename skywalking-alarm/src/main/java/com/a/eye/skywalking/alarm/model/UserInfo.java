package com.a.eye.skywalking.alarm.model;

public class UserInfo {

    private String userId;

    private String userName;

    public UserInfo(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
