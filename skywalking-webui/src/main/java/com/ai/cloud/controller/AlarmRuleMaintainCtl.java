package com.ai.cloud.controller;

import com.ai.cloud.service.inter.IAlarmRuleSer;
import com.ai.cloud.vo.mvo.AlarmRuleMVO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/alarm-rule")
public class AlarmRuleMaintainCtl {

    @Autowired
    IAlarmRuleSer alarmRuleSer;

    private static Logger logger = LogManager.getLogger(AlarmRuleMaintainCtl.class);

    @RequestMapping("/toCreate")
    public String create() {
        return "alarm-rule/createAlarm-rule";
    }

    @RequestMapping("/list/{appId}")
    public String listInfo(HttpServletRequest request, ModelMap model, @PathVariable("appId") String appId, String ruleId, String appCode,String isGlobal) {
        HttpSession session = request.getSession();
        String uid = (String) session.getAttribute("uid");
        AlarmRuleMVO ruleMVO = new AlarmRuleMVO();
        if (!"default".equals(appId)) {
            ruleMVO.setAppId(appId);
        }
        ruleMVO.setUid(uid);
        if (!"default".equals(appId)) {
            ruleMVO = alarmRuleSer.queryAppAlarmRule(ruleMVO);
            ruleMVO.setAppId(appId);
        } else {
            ruleMVO = alarmRuleSer.queryUserDefaultAlarmRule(ruleMVO);
        }

        ruleMVO.setAppCode(appCode);
        model.addAttribute("ruleMVO", ruleMVO);
        ruleMVO.setIsGlobal(isGlobal);
        return "alarm-rule/createAlarm-rule";
    }
}
