package com.ai.cloud.skywalking.sample.dubbo.impl;

import com.ai.cloud.skywalking.sample.dubbo.interfaces.SampleDubboInterface;
import com.ai.cloud.skywalking.sample.service.inter.SampleServiceInterface;
import com.alibaba.dubbo.config.annotation.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class SampleDubboInterfaceImpl implements SampleDubboInterface {

    private Logger logger = LogManager.getLogger(SampleDubboInterfaceImpl.class);

    @Autowired
    private SampleServiceInterface sampleServiceInterface;

    public String callMethodByDubbox(String value) {
        logger.info("Begin to call service method.");
        return sampleServiceInterface.saveSampleTable1(value);
    }
}
