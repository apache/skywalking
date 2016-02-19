package com.ai.cloud.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AppMaintainCtl {
    @RequestMapping("/app/create")
    public String create() {
        return "app/newApp";
    }
}
