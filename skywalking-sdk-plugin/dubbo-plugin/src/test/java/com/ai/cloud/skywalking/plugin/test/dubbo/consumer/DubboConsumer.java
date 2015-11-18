package com.ai.cloud.skywalking.plugin.test.dubbo.consumer;

import com.ai.cloud.skywalking.plugin.test.dubbo.interfaces.IDubboInterA;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboConsumer {

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:consumer/dubbo-consumer.xml");
        IDubboInterA dubboInterA = context.getBean(IDubboInterA.class);
        dubboInterA.doBusiness("AAAAA");
        Thread.sleep(10000000L);
    }
}
