package com.ai.cloud.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/app")
public class AppMaintainCtl {
    @RequestMapping("/create")
    public String create() {
        return "app/createApp";
    }
}
