package com.a.eye.skywalking.web.controller;

import com.a.eye.skywalking.web.common.BaseController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by xin on 16-4-10.
 */
@Controller
public class MainPageController extends BaseController{

    @RequestMapping("/mainPage")
    public String mainPage(String loadType, String key,HttpServletRequest request){
        request.setAttribute("loadType", loadType);
        request.setAttribute("key", key);
        return "main";
    }
}
