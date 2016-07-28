package com.ai.cloud.skywalking.sample.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboStart {

    private static Logger logger = LogManager.getLogger(DubboStart.class);

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath*:spring-context.xml");
        context.start();
        logger.info("Dubbo started success.");
        while (true){
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
