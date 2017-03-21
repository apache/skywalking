package com.a.eye.skywalking.collector.worker.tools;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class UrlToolsTestCase {

    @Test
    public void testParseTomcat() {
        String peers =
                UrlTools.parse("http://172.0.0.1:8080/Web/GetUser", "Tomcat");
        Assert.assertEquals(peers, "http://172.0.0.1:8080");

        peers = UrlTools.parse("https://172.0.0.1:8080/Web/GetUser", "Tomcat");
        Assert.assertEquals(peers, "https://172.0.0.1:8080");

        peers = UrlTools.parse("172.0.0.1:8080/Web/GetUser", "Tomcat");
        Assert.assertEquals(peers, "172.0.0.1:8080");

        peers = UrlTools.parse("http172.0.0.18080/Web/GetUser", "Tomcat");
        Assert.assertEquals(peers, "http172.0.0.18080/Web/GetUser");
    }

    @Test
    public void testParseMotan() {
        String peers =
                UrlTools.parse("motan://10.20.3.15:3000/com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)", "Motan");
        Assert.assertEquals(peers, "motan://10.20.3.15:3000");
    }

}
