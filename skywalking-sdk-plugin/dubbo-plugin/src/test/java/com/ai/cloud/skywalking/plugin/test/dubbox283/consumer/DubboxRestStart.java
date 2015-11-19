package com.ai.cloud.skywalking.plugin.test.dubbox283.consumer;

import com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboxRestStart {

    public static void main(String[] args) throws InterruptedException {
        new BugFixAcitve();
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext("classpath*:provider/dubbox283-provider.xml");

        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
