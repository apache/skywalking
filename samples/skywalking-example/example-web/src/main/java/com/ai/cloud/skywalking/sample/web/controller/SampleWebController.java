package com.ai.cloud.skywalking.sample.web.controller;

import com.ai.cloud.skywalking.sample.dubbo.interfaces.SampleDubboInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

@Controller
@RequestMapping("/sample")
public class SampleWebController {
    private Logger logger = LogManager.getLogger(SampleWebController.class);
    @Autowired
    private SampleDubboInterface sampleDubboInterface;

    @RequestMapping("/normal")
    public ModelAndView samplePath(){
        logger.info("Start.....");
        ModelAndView modelAndView = new ModelAndView("saveSuccess");
        String generateKey = sampleDubboInterface.callMethodByDubbox(UUID.randomUUID().toString());
        modelAndView.addObject("key",generateKey);
        return modelAndView;
    }
}
