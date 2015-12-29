package com.ai.cloud.skywalking.plugin.spring.test;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TestBean/* implements TestInterface*/ {
    private String value;

    public void testPrintln(String value) {
        System.out.println(value);
    }

    private void testPrintln2(String value) {
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
