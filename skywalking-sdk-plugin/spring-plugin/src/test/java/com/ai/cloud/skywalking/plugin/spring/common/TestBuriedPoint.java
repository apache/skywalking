package com.ai.cloud.skywalking.plugin.spring.common;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestBuriedPoint {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:springConfig-common.xml");
        TestBuriedPointBean testBuriedPointBean = classPathXmlApplicationContext.getBean(TestBuriedPointBean.class);
        testBuriedPointBean.sayTest();
        System.out.println(testBuriedPointBean.addStr(1));
    }
}
