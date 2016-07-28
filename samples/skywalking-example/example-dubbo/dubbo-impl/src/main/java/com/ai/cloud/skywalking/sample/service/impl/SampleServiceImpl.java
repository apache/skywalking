package com.ai.cloud.skywalking.sample.service.impl;

import com.ai.cloud.skywalking.sample.mybatis.dao.Sampletable1Mapper;
import com.ai.cloud.skywalking.sample.mybatis.dao.Sampletable2Mapper;
import com.ai.cloud.skywalking.sample.mybatis.model.Sampletable1;
import com.ai.cloud.skywalking.sample.mybatis.model.Sampletable2;
import com.ai.cloud.skywalking.sample.service.inter.SampleServiceInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class SampleServiceImpl implements SampleServiceInterface {

    private Logger logger = LogManager.getLogger(SampleServiceImpl.class);

    @Autowired
    private Sampletable1Mapper sampletable1Mapper;

    @Autowired
    private Sampletable2Mapper sampletable2Mapper;

    public String saveSampleTable1(String value) {
        String generateKey = UUID.randomUUID().toString();
        logger.info("Generate key : {}.", generateKey);
        logger.info("Save to sampletable1");
        sampletable1Mapper.insert(generateSampleObject1(generateKey,value));
        logger.info("Save to sampletable2");
        sampletable2Mapper.insert(generateSampleObject2(generateKey,value));
        return generateKey;
    }

    private Sampletable2 generateSampleObject2(String generateKey, String value) {
        Sampletable2 sampletable2 = new Sampletable2();
        sampletable2.setKey2(generateKey);
        sampletable2.setValue2(value);
        return sampletable2;
    }

    private Sampletable1 generateSampleObject1(String generateKey, String value) {
        Sampletable1 sampletable1 = new Sampletable1();
        sampletable1.setKey1(generateKey);
        sampletable1.setValue1(value);
        return sampletable1;
    }
}
