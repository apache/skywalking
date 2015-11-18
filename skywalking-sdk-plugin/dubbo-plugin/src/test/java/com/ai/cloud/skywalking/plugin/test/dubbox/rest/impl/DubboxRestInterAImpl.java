package com.ai.cloud.skywalking.plugin.test.dubbox.rest.impl;

import com.ai.cloud.skywalking.plugin.test.dubbox.rest.interfaces.IDubboxRestInterA;
import com.alibaba.dubbo.config.annotation.Service;

@Service
public class DubboxRestInterAImpl implements IDubboxRestInterA {
    public String doBusiness(String paramA) {
        System.out.println("param : " + paramA);
        return "{\"content\":\"" + paramA + "\"}";
    }
}
