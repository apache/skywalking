package test.apache.skywalking.testcase.jettyclient.controller;

import javax.annotation.PostConstruct;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class CaseController {

    @Value(value = "${jettyServer.host:localhost}")
    private String jettyServerHost;

    private HttpClient client = new HttpClient();

    @PostConstruct
    public void init() throws Exception {
        client.start();
    }

    @RequestMapping("/jettyclient-case")
    @ResponseBody
    public String jettyClientScenario() throws Exception {
        client.newRequest("http://" + jettyServerHost + ":18080/jettyserver-case/case/receiveContext-0").send();
        return "Success";
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws Exception {
        return "Success";
    }
}
