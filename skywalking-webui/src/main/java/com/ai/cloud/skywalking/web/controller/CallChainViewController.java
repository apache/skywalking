package com.ai.cloud.skywalking.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Created by xin on 16-3-19.
 */
@Controller
@RequestMapping("/trace")
public class CallChainViewController {

    @RequestMapping("/view")
    public String view(){
        return "";
    }
}
