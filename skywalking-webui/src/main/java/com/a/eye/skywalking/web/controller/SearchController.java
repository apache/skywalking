package com.a.eye.skywalking.web.controller;

import com.a.eye.skywalking.web.common.BaseController;
import com.a.eye.skywalking.web.dto.TraceTreeInfo;
import com.a.eye.skywalking.web.service.inter.ITraceTreeService;
import com.a.eye.skywalking.web.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by xin on 16-3-29.
 */
@Controller
public class SearchController extends BaseController {

    @Autowired
    private ITraceTreeService iTraceTreeService;

    private Logger logger = LogManager.getLogger(SearchController.class);

    @RequestMapping(value = "")
    public String showDefaultIndexPage(ModelMap root) throws Exception {
        return "index";
    }

    @RequestMapping(value = "/search/traceId", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String loadTraceTree(@RequestParam("traceId") String traceId) {
        JSONObject jsonObject = new JSONObject();
        try {
            if (StringUtil.isBlank(traceId)) {
                jsonObject.put("code", "400");
                jsonObject.put("result", "TraceId cannot be null");
                return jsonObject.toJSONString();
            }

            TraceTreeInfo traceTree = iTraceTreeService.queryTraceTreeByTraceId(traceId);
            if (traceTree != null) {
                jsonObject.put("code", "200");
                jsonObject.put("result", JSON.toJSONString(traceTree));
            } else {
                jsonObject.put("code", "500");
                jsonObject.put("message", "Cannot find TraceId[" + traceId + "]");
            }
        } catch (Exception e) {
            logger.error("Search tree Id ", e);
            jsonObject.put("code", "500");
            jsonObject.put("result", "Fatal error");
        }

        return jsonObject.toJSONString();
    }

    @RequestMapping(value = "/{traceId:.+}")
    public String searchTrace(@PathVariable String traceId, HttpServletRequest request) {
        System.out.println("search Trace.....");
        request.setAttribute("key", traceId);
        request.setAttribute("searchType", "TRACE_ID");
        request.setAttribute("loadType", "showTraceInfo");
        return "main";
    }

    @RequestMapping(value = "/search/chainTree", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String searchCallChainTree(String key, HttpServletRequest request, int pageSize) {
        //TODO: not provided in this release
        return "";
    }
}
