package com.ai.cloud.skywalking.web.controller;

import com.sun.org.apache.xpath.internal.operations.String;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PortalController {

    @RequestMapping("/index")
    public String index() {
        return null;
    }
}
