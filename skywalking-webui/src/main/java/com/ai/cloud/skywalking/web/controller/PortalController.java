package com.ai.cloud.skywalking.web.controller;

import com.ai.cloud.skywalking.web.common.BaseController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PortalController extends BaseController{

    @RequestMapping("/index")
    public String index() {
        return "index";
    }
}
