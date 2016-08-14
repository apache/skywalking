package com.a.eye.skywalking.web.controller;

import com.a.eye.skywalking.web.common.BaseController;
import com.a.eye.skywalking.web.dao.inter.IAlarmRuleMaintainDao;
import com.a.eye.skywalking.web.dao.inter.IApplicationsMaintainDao;
import com.a.eye.skywalking.web.dto.ApplicationInfo;
import com.a.eye.skywalking.web.dto.LoginUserInfo;
import com.a.eye.skywalking.web.util.StringUtil;
import com.a.eye.skywalking.web.entity.AlarmRule;
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
import java.sql.Timestamp;
import java.util.List;

/**
 * Created by xin on 16-3-25.
 */
@Controller
@RequestMapping("/usr/applications")
public class ApplicationConfigController extends BaseController {

    private Logger logger = LogManager.getLogger(ApplicationConfigController.class);

    @Autowired
    private IAlarmRuleMaintainDao alarmRuleMaintainDao;

    @Autowired
    private IApplicationsMaintainDao applicationsMaintainDao;

    @RequestMapping("/list")
    public String applicationConfig() {
        return "usr/applications/applicationsconfig";
    }

    @RequestMapping("/add")
    public String addApplication() {
        return "usr/applications/addapplication";
    }

    @RequestMapping(value = "/all", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String listAllApplications(HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
            List<ApplicationInfo> applicationList =
                    applicationsMaintainDao.queryAllApplications(loginUserInfo.getUid());

            jsonObject.put("code", "200");
            jsonObject.put("result", new Gson().toJson(applicationList));
        } catch (Exception e) {
            logger.error("Failed to load all applications", e);
            jsonObject.put("code", "500");
            jsonObject.put("message", "Fatal error");
        }

        return jsonObject.toJSONString();
    }


    @RequestMapping(value = "/dosave", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String saveApplication(String appInfo, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            ApplicationInfo application = new Gson().fromJson(appInfo, ApplicationInfo.class);
            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
            AlarmRule defaultAlarmRule = alarmRuleMaintainDao.queryGlobalAlarmRule(loginUserInfo.getUid());
            if (application.isGlobalConfig() && defaultAlarmRule == null) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "you don't have global alarm rules");
                return jsonObject.toJSONString();
            }
            application.setUId(loginUserInfo.getUid());
            application.setSts("A");
            applicationsMaintainDao.saveApplication(application);
            if (application.isGlobalConfig()) {
                if (application.isUpdateGlobalConfig()) {
                    defaultAlarmRule.setConfigArgs(application.getConfigArgs());
                    defaultAlarmRule.setTodoType("0");
                    alarmRuleMaintainDao.updateAlarmRule(defaultAlarmRule);
                }
            } else {
                AlarmRule alarmRule = new AlarmRule();
                alarmRule.setIsGlobal("0");
                alarmRule.setTodoType("0");
                alarmRule.setSts("A");
                alarmRule.setAppId(application.getAppId());
                alarmRule.setUid(loginUserInfo.getUid());
                alarmRule.setConfigArgs(application.getConfigArgs());
                alarmRuleMaintainDao.saveAlarmRule(alarmRule);
            }

            jsonObject.put("code", "200");
            jsonObject.put("message", "save application success");
        } catch (Exception e) {
            logger.error("Failed to save applicationInfo[{}]", appInfo, e);
            jsonObject.put("code", "500");
            jsonObject.put("message", "Fatal error");
        }

        return jsonObject.toJSONString();
    }

    @RequestMapping("/modify/{applicationId}")
    public String modifyApplication(@PathVariable("applicationId") String applicationId, HttpServletRequest request) {
        request.setAttribute("applicationId", "applicationId");
        return "usr/applications/modifyapplication";
    }

    @RequestMapping(value = "/update/{applicationId}", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String updateApplication(@PathVariable("applicationId") String applicationId, String appInfo, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            if (StringUtil.isBlank(applicationId)) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "applicationId cannot be null");
                return jsonObject.toJSONString();
            }

            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
            ApplicationInfo application = applicationsMaintainDao.loadApplication(applicationId, loginUserInfo.getUid());
            if (application == null) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "Failed to find appId[" + applicationId + "]");
                return jsonObject.toJSONString();
            }

            ApplicationInfo applicationData = new Gson().fromJson(appInfo, ApplicationInfo.class);

            applicationData.setAppId(applicationId);
            //原始AppCode不做修改
            applicationData.setAppCode(application.getAppCode());
            applicationData.setUId(loginUserInfo.getUid());
            applicationData.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            applicationsMaintainDao.modifyApplication(application);

            AlarmRule selfAlarmRule = alarmRuleMaintainDao.queryAlarmRule(loginUserInfo.getUid(), applicationId);
            if (applicationData.isGlobalConfig()) {
                if (selfAlarmRule != null) {
                    //失效掉原来的SelfAlarmRule
                    selfAlarmRule.setSts("D");
                    alarmRuleMaintainDao.updateAlarmRule(selfAlarmRule);
                }

                if (applicationData.isUpdateGlobalConfig()) {
                    //Insert global alarm rules
                    AlarmRule alarmRule = new AlarmRule();
                    alarmRule.setConfigArgs(applicationData.getConfigArgs());
                    alarmRule.setIsGlobal("1");
                    alarmRule.setUid(loginUserInfo.getUid());
                    alarmRule.setTodoType("0");
                    alarmRule.setSts("A");
                    alarmRuleMaintainDao.saveAlarmRule(alarmRule);
                } else {
                    // update global alarm rules
                    AlarmRule global = alarmRuleMaintainDao.queryGlobalAlarmRule(loginUserInfo.getUid());
                    global.setTodoType("0");
                    global.setConfigArgs(applicationData.getConfigArgs());
                    alarmRuleMaintainDao.updateAlarmRule(global);
                }
            } else {

                if (selfAlarmRule != null) {
                    // update self alarm rules
                    selfAlarmRule.setConfigArgs(applicationData.getConfigArgs());
                    alarmRuleMaintainDao.updateAlarmRule(selfAlarmRule);
                } else {
                    AlarmRule alarmRule = new AlarmRule();
                    alarmRule.setIsGlobal("0");
                    alarmRule.setTodoType("0");
                    alarmRule.setSts("A");
                    alarmRule.setAppId(applicationData.getAppId());
                    alarmRule.setUid(loginUserInfo.getUid());
                    alarmRule.setConfigArgs(applicationData.getConfigArgs());
                    alarmRuleMaintainDao.saveAlarmRule(alarmRule);
                }
            }

            jsonObject.put("code", "200");
            jsonObject.put("message", "update application success");
        } catch (Exception e) {
            logger.error("Failed to update applicationInfo[{}]", appInfo, e);
            jsonObject.put("code", "500");
            jsonObject.put("message", "Fatal error");
        }
        return jsonObject.toJSONString();
    }


    @RequestMapping(value = "/load/{applicationId}", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String loadApplication(@PathVariable("applicationId") String applicationId, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            if (StringUtil.isBlank(applicationId)) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "applicationId cannot be null");
                return jsonObject.toJSONString();
            }

            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);
            ApplicationInfo application = applicationsMaintainDao.loadApplication(applicationId, loginUserInfo.getUid());

            if (application == null) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "Cannot find this application");
                return jsonObject.toJSONString();
            }

            if (alarmRuleMaintainDao.queryGlobalAlarmRule(loginUserInfo.getUid()) != null) {
                application.setHasGlobalAlarmRule(true);
            }

            if (alarmRuleMaintainDao.queryAlarmRule(loginUserInfo.getUid(), applicationId) == null) {
                application.setGlobalAlarmRule(true);
            }

            jsonObject.put("code", "200");
            jsonObject.put("result", new Gson().toJson(application));
        } catch (Exception e) {
            logger.error("Failed to save applicationInfo[{}]", applicationId, e);
            jsonObject.put("code", "500");
            jsonObject.put("message", "Fatal error");
        }
        return jsonObject.toJSONString();
    }

    @RequestMapping(value = "/del/{applicationId}", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String delApplication(@PathVariable("applicationId") String applicationId, HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        try {
            if (StringUtil.isBlank(applicationId)) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "applicationId cannot be null");
                return jsonObject.toJSONString();
            }

            LoginUserInfo loginUserInfo = fetchLoginUserInfoFromSession(request);

            ApplicationInfo application = applicationsMaintainDao.loadApplication(applicationId, loginUserInfo.getUid());
            if (application == null) {
                jsonObject.put("code", "500");
                jsonObject.put("message", "Failed to find appId[" + applicationId + "]");
                return jsonObject.toJSONString();
            }

            AlarmRule selfAlarmRule = alarmRuleMaintainDao.queryAlarmRule(loginUserInfo.getUid(), applicationId);
            if (selfAlarmRule != null) {
                //失效掉原来的SelfAlarmRule
                selfAlarmRule.setSts("D");
                alarmRuleMaintainDao.updateAlarmRule(selfAlarmRule);
            }

            applicationsMaintainDao.delApplication(loginUserInfo.getUid(), application.getAppId());
            jsonObject.put("code", "200");
            jsonObject.put("message", "delete application success");
        } catch (Exception e) {
            logger.error("Failed to save applicationInfo[{}]", applicationId, e);
            jsonObject.put("code", "500");
            jsonObject.put("message", "Fatal error");
        }
        return jsonObject.toJSONString();
    }
}
