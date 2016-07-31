package com.ai.cloud.skywalking.sample.dubbo.impl;

import com.ai.cloud.skywalking.sample.dubbo.interfaces.SampleDubboInterface;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by xin on 16/7/31.
 */
public class SampleDubboInterfaceImplTest {

    @Test
    public void testSave(){
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:consumer/dubbo-consumer.xml");
        SampleDubboInterface sampleDubboInterface = classPathXmlApplicationContext.getBean(SampleDubboInterface.class);
        System.out.println(sampleDubboInterface.callMethodByDubbox("Value"));
    }
}
