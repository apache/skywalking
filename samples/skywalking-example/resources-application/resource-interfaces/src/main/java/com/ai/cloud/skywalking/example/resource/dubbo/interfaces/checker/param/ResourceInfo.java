package com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.param;

import java.io.Serializable;

public class ResourceInfo implements Serializable{
    private String phoneNumber;
    private String resourceId;
    //手机套餐
    private String phonePackage;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getPhonePackage() {
        return phonePackage;
    }

    public void setPhonePackage(String phonePackage) {
        this.phonePackage = phonePackage;
    }
}
