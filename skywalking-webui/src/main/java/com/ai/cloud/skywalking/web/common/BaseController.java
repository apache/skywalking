package com.ai.cloud.skywalking.web.common;

import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BaseController {
    @ModelAttribute
    public void initPath(HttpServletRequest request, HttpServletResponse response, ModelMap model) {
        String base = request.getContextPath();
        String fullPath = request.getScheme() + "://" + request.getServerName() +
                ":" + request.getServerPort() + base;
        model.addAttribute("_base", base);

    }
}
