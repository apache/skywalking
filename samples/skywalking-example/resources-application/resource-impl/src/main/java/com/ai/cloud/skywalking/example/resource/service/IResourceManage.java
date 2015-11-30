package com.ai.cloud.skywalking.example.resource.service;

import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.param.ResourceInfo;
import com.ai.cloud.skywalking.example.resource.exception.BusinessException;

public interface IResourceManage {
    boolean checkResource(ResourceInfo resourceInfo);

    boolean reservationResource(ResourceInfo resourceInfo);
}
