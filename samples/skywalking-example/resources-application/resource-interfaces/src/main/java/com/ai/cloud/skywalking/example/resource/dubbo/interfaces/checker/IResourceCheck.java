package com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker;

import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.param.ResourceInfo;

public interface IResourceCheck {
    boolean checkResource(ResourceInfo info);
    boolean reservationResource(ResourceInfo info);
}
