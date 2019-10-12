package org.apache.skywalking.apm.testcase.sc.gateway.projectA;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhaoyuguang
 */
@RestController
public class TestController {

    @RequestMapping("/ping")
    public String ping(){
        return "pong";
    }
}
