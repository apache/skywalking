package com.ai.cloud.skywalking.web.controller;

import com.ai.cloud.skywalking.web.bo.AlarmRuleInfo;
import com.ai.cloud.skywalking.web.bo.LoginUserInfo;
import com.ai.cloud.skywalking.web.common.BaseController;
import com.ai.cloud.skywalking.web.dao.inter.IAlarmRuleMaintainDao;
import com.ai.cloud.skywalking.web.entity.AlarmRule;
import com.ai.cloud.util.common.StringUtil;
import com.ai.cloud.vo.svo.ConfigArgs;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by xin on 16-3-27.
 */
@RequestMapping("/usr/applications/alarm-rule")
@Controller
public class AlarmRuleMaintainController extends BaseController {

    private Logger logger = LogManager.getLogger(AlarmRuleMaintainController.class);

    @Autowired
    private IAlarmRuleMaintainDao iAlarmRuleMaintainDao;


    @RequestMapping(value = "/global", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String listDefaultAlarm(HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
            AlarmRule rule = iAlarmRuleMaintainDao.queryGlobalAlarmRule(loginUserInfo.getUid());
            logger.info("login user ID[{}], default alarm rule: {}", loginUserInfo.getUid(), rule);
            jsonObject.put("code", "200");
            if (rule != null && rule.getConfigArgs() != null) {
                jsonObject.put("result", new Gson().toJson(rule));
            } else {
                jsonObject.put("result", "");
            }
        } catch (Exception e) {
            logger.error("Failed to query default rule.", e);
            jsonObject.put("code", "500");
            jsonObject.put("result", "Fatal error.");
        }

        return jsonObject.toJSONString();
    }


    @RequestMapping("/global/create")
    public String createDefaultConfig() {
        return "usr/applications/createglobalconfig";
    }

    @RequestMapping(value = "/global/save", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String saveGlobalConfig(String configArgs, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
            if (iAlarmRuleMaintainDao.queryGlobalAlarmRule(loginUserInfo.getUid()) != null) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "The global has been already.");
                return jsonObject.toJSONString();
            }

            AlarmRule alarmRule = new AlarmRule();
            alarmRule.setConfigArgs(new Gson().fromJson(configArgs, ConfigArgs.class));
            alarmRule.setIsGlobal("1");
            alarmRule.setUid(loginUserInfo.getUid());
            alarmRule.setTodoType("0");
            alarmRule.setSts("A");

            iAlarmRuleMaintainDao.saveAlarmRule(alarmRule);
            jsonObject.put("code", "200");
            jsonObject.put("message", "Save global config sucessful");
            logger.info("configArgs[{}]", configArgs);
        } catch (Exception e) {
            logger.error("Failed to save global save, the config:{}", configArgs, e);
            jsonObject.put("code", "500");
            jsonObject.put("message", "Fatal error");
        }
        return jsonObject.toJSONString();
    }

    @RequestMapping(value = "/load/{applicationId}", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String loadAlarmRule(@PathVariable("applicationId") String applicationId, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            if (StringUtil.isBlank(applicationId)) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "application Id cannot be null");
                return jsonObject.toJSONString();
            }

            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
            AlarmRule rule = iAlarmRuleMaintainDao.queryAlarmRule(loginUserInfo.getUid(), applicationId);
            AlarmRule globalRule = iAlarmRuleMaintainDao.queryGlobalAlarmRule(loginUserInfo.getUid());
            logger.info("selfAlarmRule:{}, globalRule:{}", (rule == null), globalRule == null);
            AlarmRuleInfo alarmRuleInfo = new AlarmRuleInfo(rule, globalRule);
            jsonObject.put("code", "200");
            jsonObject.put("result", new Gson().toJson(alarmRuleInfo));
        } catch (Exception e) {
            logger.error("Failed to save global save, the applicationId:{}", applicationId, e);
            jsonObject.put("code", "500");
            jsonObject.put("message", "Fatal error");
        }

        return jsonObject.toJSONString();
    }
}
