package com.ai.cloud.skywalking.plugin.spring.test;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AppTest01 {

    public void testPrintln(String value){
        System.out.println(value);
    }


    @Test
    public void testBean(){
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:springConfig-common.xml");
        AppTest01 appTest01 = classPathXmlApplicationContext.getBean(AppTest01.class);
        appTest01.testPrintln("Hello World");
    }
}
