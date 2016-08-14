package com.a.eye.skywalking.sample.dubboxrest;

import com.a.eye.skywalking.sample.dubboxrest.interfaces.IDubboxRestInterA;
import com.a.eye.skywalking.sample.dubboxrest.interfaces.param.DubboxRestInterAParameter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.net.URISyntaxException;

public class DubboxRestConsumer {
    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:consumer/dubbox-rest-consumer.xml");
        IDubboxRestInterA dubboxRestInterA = context.getBean(IDubboxRestInterA.class);
        System.out.println(dubboxRestInterA.doBusiness(new DubboxRestInterAParameter("AAAAA")));
    }
}
