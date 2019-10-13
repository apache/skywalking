package org.apache.skywalking.apm.testcase.spring.async;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;

/**
 * @author zhaoyuguang
 */
public class AsyncBean {

    @Autowired
    private HttpBean httpBean;

    @Async
    public void sendVisitBySystem() throws IOException {
        httpBean.visit("http://skywalking.apache.org/?k=v");
    }

    @Async("customizeAsync")
    public void sendVisitByCustomize() throws IOException {
        httpBean.visit("http://skywalking.apache.org/?k=v");
    }
}
