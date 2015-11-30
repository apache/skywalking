package com.ai.cloud.skywalking.example.resource.service.impl;

import com.ai.cloud.skywalking.example.resource.dao.ResourceDAO;
import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.param.ResourceInfo;
import com.ai.cloud.skywalking.example.resource.exception.BusinessException;
import com.ai.cloud.skywalking.example.resource.service.IResourceManage;
import com.ai.cloud.skywalking.plugin.spring.Tracing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResourceManageImpl implements IResourceManage {

    @Autowired
    private ResourceDAO resourceDAO;


    @Override
    @Tracing
    public boolean checkResource(ResourceInfo resourceInfo) throws BusinessException {
        return checkPhoneNumber(resourceInfo.getPhoneNumber()) && checkResourceId(resourceInfo.getResourceId()) &&
                checkPhonePackage(resourceInfo.getPhonePackage());
    }

    @Override
    @Tracing
    public boolean reservationResource(ResourceInfo resourceInfo) {
        return resourceDAO.updatePhoneNumberStatus(resourceInfo.getPhoneNumber())
                && resourceDAO.updateResourceStatus(resourceInfo.getResourceId());
    }

    @Tracing
    public boolean checkPhoneNumber(String phoneNumber) throws BusinessException {
        if (resourceDAO.selectPhoneNumberStatus(phoneNumber) == null) {
            throw new BusinessException("Cannot find phone number[" + phoneNumber + "]");
        }

        if ("0".equals(resourceDAO.selectPhoneNumberStatus(phoneNumber))) {
            return true;
        } else {
            return false;
        }

    }

    @Tracing
    public boolean checkResourceId(String resourceId) throws BusinessException {
        if (resourceDAO.selectResourceStatus(resourceId) == null) {
            throw new BusinessException("Cannot find resourceId[" + resourceId + "]");
        }

        if ("0".equals(resourceDAO.selectResourceStatus(resourceId))) {
            return true;
        } else {
            return false;
        }
    }

    @Tracing
    public boolean checkPhonePackage(String packageId) throws BusinessException {
        if (resourceDAO.selectResourceStatus(packageId) == null) {
            throw new BusinessException("Cannot find package Id[" + packageId + "]");
        }

        if ("0".equals(resourceDAO.selectPhonePackageStatus(packageId))) {
            return true;
        } else {
            return false;
        }
    }
}
