package com.a.eye.skywalking.web.controller;

import com.a.eye.skywalking.registry.RegistryCenterFactory;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.impl.zookeeper.ZookeeperConfig;
import com.a.eye.skywalking.util.StringUtil;
import com.a.eye.skywalking.web.client.routing.RoutingServerWatcher;
import com.a.eye.skywalking.web.common.BaseController;
import com.a.eye.skywalking.web.config.Config;
import com.a.eye.skywalking.web.config.ConfigInitializer;
import com.a.eye.skywalking.web.dto.TraceTreeInfo;
import com.a.eye.skywalking.web.service.inter.ITraceTreeService;
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

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Properties;

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

    @PostConstruct
    public void init() throws IOException, IllegalAccessException {
        Properties properties = new Properties();
        properties.load(SearchController.class.getResourceAsStream("/config.properties"));
        ConfigInitializer.initialize(properties, Config.class);

        RegistryCenter center = RegistryCenterFactory.INSTANCE.getRegistryCenter(Config.RegistryCenter.TYPE);

        center.start(fetchRegistryCenterConfig());
        center.subscribe(Config.RoutingNode.SUBSCRIBE_PATH, new RoutingServerWatcher());
    }

    @RequestMapping(value = "/search/traceId", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String loadTraceTree(@RequestParam("traceId") String traceId) {
        JSONObject jsonObject = new JSONObject();
        try {
            if (StringUtil.isEmpty(traceId)) {
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

    private static Properties fetchRegistryCenterConfig() {
        Properties centerConfig = new Properties();
        centerConfig.setProperty(ZookeeperConfig.CONNECT_URL, Config.RegistryCenter.CONNECT_URL);
        centerConfig.setProperty(ZookeeperConfig.AUTH_SCHEMA, Config.RegistryCenter.AUTH_SCHEMA);
        centerConfig.setProperty(ZookeeperConfig.AUTH_INFO, Config.RegistryCenter.AUTH_INFO);
        return centerConfig;
    }
}
