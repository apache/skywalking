package com.ai.cloud.skywalking.plugin.test.dubbox283.consumer;

import com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve;
import com.ai.cloud.skywalking.plugin.test.dubbox283.interfaces.IDubboxRestInterA;
import com.ai.cloud.skywalking.plugin.test.dubbox283.interfaces.param.DubboxRestInterAParameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.net.URISyntaxException;

public class DubboxRestConsumer {
    private static final Log logger = LogFactory.getLog(DubboxRestConsumer.class);

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        new BugFixAcitve();
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:consumer/dubbox283-consumer.xml");
        IDubboxRestInterA dubboxRestInterA = context.getBean(IDubboxRestInterA.class);
        dubboxRestInterA.doBusiness(new DubboxRestInterAParameter("AAAAA"));
        Thread.sleep(10000000L);
    }
}
