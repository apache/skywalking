package com.ai.cloud.skywalking.plugin.spring;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestBean {
    private String value;

    public void testPrintln(String value) {
        System.out.println(value);
    }


    public static void main(String[] args) throws IllegalAccessException {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:springConfig-common.xml");
        TestBean testBean = classPathXmlApplicationContext.getBean(TestBean.class);
        testBean.testPrintln("Hello World");
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
