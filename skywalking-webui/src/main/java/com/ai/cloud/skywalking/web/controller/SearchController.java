package com.ai.cloud.skywalking.web.controller;

import com.ai.cloud.skywalking.web.common.BaseController;
import com.ai.cloud.skywalking.web.dto.AnlyResult;
import com.ai.cloud.skywalking.web.dto.CallChainNode;
import com.ai.cloud.skywalking.web.dto.LoginUserInfo;
import com.ai.cloud.skywalking.web.dto.TraceTreeInfo;
import com.ai.cloud.skywalking.web.entity.CallChainTree;
import com.ai.cloud.skywalking.web.service.inter.ICallChainTreeService;
import com.ai.cloud.skywalking.web.service.inter.ITraceTreeService;
import com.ai.cloud.skywalking.web.util.Constants;
import com.ai.cloud.skywalking.web.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.*;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-3-29.
 */
@Controller
public class SearchController extends BaseController {

    @Autowired
    private ITraceTreeService iTraceTreeService;

    @Autowired
    private ICallChainTreeService callChainTreeService;
    private Logger logger = LogManager.getLogger(SearchController.class);


    @RequestMapping(value = "")
    public String showDefaultIndexPage(ModelMap root) throws Exception {
        return "index";
    }

    @RequestMapping(value = "/searchResult")
    public String searchResult() {
        return "";
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
        JSONObject jsonObject = new JSONObject();
        try {
            if (StringUtil.isBlank(key)) {
                jsonObject.put("code", "200");
                jsonObject.put("result", JSON.toJSONString(new ArrayList<CallChainTree>()));
                return jsonObject.toJSONString();
            }

            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);

            List<CallChainTree> callChainTreeList =
                    callChainTreeService.queryCallChainTreeByKey(loginUserInfo.getUid(), key, pageSize);
            //List<CallChainTree> callChainTreeList = generateCallChainTree();
            JsonObject result = new JsonObject();
            if (callChainTreeList.size() > Constants.MAX_ANALYSIS_RESULT_PAGE_SIZE) {
                result.addProperty("hasNextPage", true);
                callChainTreeList.remove(callChainTreeList.size() - 1);
            } else {
                result.addProperty("hasNexPage", false);
            }
            JsonElement jsonElements =  new JsonParser().parse(new Gson().toJson(callChainTreeList));
            result.add("children", jsonElements);
            jsonObject.put("code", "200");
            jsonObject.put("result", result.toString());
        } catch (Exception e) {
            logger.error("Failed to search chain tree:{}", key, e);
            jsonObject.put("code", "500");
            jsonObject.put("result", "Fatal error");
        }
        return jsonObject.toJSONString();
    }

    private List<CallChainTree> generateCallChainTree() {
        List<CallChainTree> callChainTrees = new ArrayList<CallChainTree>();
        CallChainTree chainTree = new CallChainTree("test");
        chainTree.setEntranceViewpoint("test");
        chainTree.setTreeId("test tree id");
        List<CallChainNode> callChainNodes = new ArrayList<>();
        CallChainNode callChainNode = new CallChainNode("0.0", "test view point id", true);
        CallChainNode callChainNode1 = new CallChainNode("0.0.0", "test view point id", true);
        CallChainNode callChainNode2 = new CallChainNode("0.1", "test view point id", true);
        CallChainNode callChainNode3 = new CallChainNode("0.2", "test view point id", true);
        callChainNodes.add(callChainNode);
        callChainNodes.add(callChainNode1);
        callChainNodes.add(callChainNode2);
        callChainNodes.add(callChainNode3);
        chainTree.setNodes(callChainNodes);
        callChainTrees.add(chainTree);
        AnlyResult anlyResult = new AnlyResult();
        anlyResult.setTotalCostTime(1000);
        anlyResult.setTotalCall(20);
        anlyResult.setHumanInterruptionNumber(10);
        anlyResult.setCorrectNumber(10);
        chainTree.setEntranceAnlyResult(anlyResult);
        return callChainTrees;
    }

}
