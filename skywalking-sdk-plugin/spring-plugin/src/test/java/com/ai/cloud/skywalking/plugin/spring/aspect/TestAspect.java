package com.ai.cloud.skywalking.plugin.spring.aspect;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestAspect {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext classPathXmlApplicationContext =
                new ClassPathXmlApplicationContext("classpath*:springConfig-aspect.xml");
        TracingAspectBean testBuriedPointBean = classPathXmlApplicationContext.getBean(TracingAspectBean.class);
        testBuriedPointBean.doBusiness();
    }
}
