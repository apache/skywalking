package com.ai.cloud.skywalking.plugin.spring;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestBean {

    public void testPrintln(String value){
        System.out.println(value);
    }


    public static void main(String[] args){
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:springConfig-common.xml");
        TestBean testBean = classPathXmlApplicationContext.getBean(TestBean.class);
        testBean.testPrintln("Hello World");
    }
}