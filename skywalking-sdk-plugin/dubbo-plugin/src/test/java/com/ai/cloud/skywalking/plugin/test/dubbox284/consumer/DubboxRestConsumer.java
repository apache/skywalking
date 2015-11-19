package com.ai.cloud.skywalking.plugin.test.dubbox284.consumer;

import com.ai.cloud.skywalking.plugin.test.dubbox283.interfaces.param.DubboxRestInterAParameter;
import com.ai.cloud.skywalking.plugin.test.dubbox284.interfaces.IDubboxRestInterA;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.net.URISyntaxException;

public class DubboxRestConsumer {
    private static final Log logger = LogFactory.getLog(DubboxRestConsumer.class);

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:consumer/dubbox284-consumer.xml");
        IDubboxRestInterA dubboxRestInterA = context.getBean(IDubboxRestInterA.class);
        dubboxRestInterA.doBusiness(new DubboxRestInterAParameter("AAAAA"));
        Thread.sleep(10000000L);
    }
}
