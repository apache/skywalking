package com.ai.cloud.skywalking.web.controller;

import com.ai.cloud.skywalking.web.common.BaseController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by xin on 16-4-5.
 */
@RequestMapping("/usr/applications")
@Controller
public class AnalysisResultController extends BaseController {

    @RequestMapping("/anlsResult")
    public String analysisResult() {
        return "anls-result/analysisResult";
    }

    //@RequestMapping(value = "/load/{anlsDate}", produces = "application/json; charset=UTF-8")
    //@ResponseBody
    public String loadAnalysisResult(@PathVariable("anlsDate") String anlsDate) {

        return null;
    }
}
