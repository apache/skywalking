package com.ai.cloud.controller;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ai.cloud.service.inter.IAlarmRuleSer;
import com.ai.cloud.util.Constants;
import com.ai.cloud.util.common.StringUtil;
import com.ai.cloud.vo.mvo.AlarmRuleMVO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sun.activation.registries.MailcapTokenizer;

@Controller
public class AlarmRuleCtl {

	@Autowired
	IAlarmRuleSer alarmRuleSer;

	private static Logger logger = LogManager.getLogger(AlarmRuleCtl.class);

	/***
	 * 查询用户默认规则
	 *
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/alarmRule/default", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
	@ResponseBody
	public String queryUserDefaultAlarmRule(HttpServletRequest request, ModelMap root, @RequestBody String json)
			throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");

		JSONObject reJson = new JSONObject();

		if (StringUtil.isBlank(uid)) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return reJson.toJSONString();
		}

		AlarmRuleMVO ruleMVO = new AlarmRuleMVO();
		ruleMVO.setUid(uid);

		ruleMVO = alarmRuleSer.queryUserDefaultAlarmRule(ruleMVO);

		if (ruleMVO != null && !StringUtil.isBlank(ruleMVO.getRuleId())) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "查询到默认数据");
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_DATA, JSON.toJSONString(ruleMVO));
		} else {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未查询到默认数据");
		}

		return reJson.toJSONString();
	}

	/***
	 * 查询用户默认规则
	 *
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/alarmRule/{appId}", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
	@ResponseBody
	public String queryAppAlarmRule(HttpServletRequest request, ModelMap root, @PathVariable("appId") String appId)
			throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");

		JSONObject reJson = new JSONObject();

		if (StringUtil.isBlank(uid)) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return reJson.toJSONString();
		}

		AlarmRuleMVO ruleMVO = new AlarmRuleMVO();
		ruleMVO.setAppId(appId);
		ruleMVO.setUid(uid);

		ruleMVO = alarmRuleSer.queryAppAlarmRule(ruleMVO);

		if (ruleMVO != null && !StringUtil.isBlank(ruleMVO.getRuleId())) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "查询到默认数据");
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_DATA, JSON.toJSONString(ruleMVO));
		} else {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未查询到默认数据");
		}

		return reJson.toJSONString();
	}

	/***
	 * 创建用户默认规则
	 *
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/alarmRule/create", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
	@ResponseBody
	public String createAppAlarmRule(HttpServletRequest request, ModelMap root, @RequestBody String jsonStr)
			throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");

		JSONObject reJson = new JSONObject();

		if (StringUtil.isBlank(uid)) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return reJson.toJSONString();
		}

		JSONObject json = JSON.parseObject(jsonStr);
		if (!json.containsKey("isGlobal")) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到isGlobal参数信息");
			return reJson.toJSONString();
		}
		String isGlobal = json.getString("isGlobal");
		if (!json.containsKey("period")) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到告警间隔参数信息");
			return reJson.toJSONString();
		}
		String period = json.getString("period");
		if (!json.containsKey("todoType")) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到操作类型参数信息");
			return reJson.toJSONString();
		}
		String todoType = json.getString("todoType");

		String mailTo = null;
		String mailCc = null;
		if (Constants.TODO_TYPE_0.equals(todoType)) {
			if (!json.containsKey("mailTo")) {
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到邮件发送人信息");
				return reJson.toJSONString();
			}
			mailTo = json.getString("mailTo").replace("\n", "");
			if (json.containsKey("mailCc")) {
				mailCc = json.getString("mailCc").replace("\n", "");
			}
		} else if (Constants.TODO_TYPE_1.equals(todoType)) {
			if (!json.containsKey("urlCall")) {
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到回调接口信息");
				return reJson.toJSONString();
			}
		} else {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未定义告警操作类型");
			return reJson.toJSONString();
		}

		AlarmRuleMVO srchRuleMVO = new AlarmRuleMVO();
		srchRuleMVO.setUid(uid);
		srchRuleMVO.setSts(Constants.STR_VAL_A);
		srchRuleMVO.setIsGlobal(isGlobal);

		// 判断是否为全局规则
		if (Constants.IS_GLOBAL_FALG_1.equals(isGlobal)) {
			srchRuleMVO = alarmRuleSer.queryUserDefaultAlarmRule(srchRuleMVO);
			if (srchRuleMVO != null && !StringUtil.isBlank(srchRuleMVO.getRuleId())) {
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "默认告警规则已经存在");
				return reJson.toJSONString();
			}
		} else {
			if (!json.containsKey("appId")) {
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到appId参数信息");
				return reJson.toJSONString();
			} else {
				srchRuleMVO.setAppId(json.getString("appId"));
				srchRuleMVO = alarmRuleSer.queryAppAlarmRule(srchRuleMVO);
				if (srchRuleMVO != null && !StringUtil.isBlank(srchRuleMVO.getRuleId())) {
					reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
					reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "当前应用已经存在告警规则");
					return reJson.toJSONString();
				}
			}
		}

		AlarmRuleMVO ruleMVO = new AlarmRuleMVO();
		ruleMVO.setUid(uid);
		ruleMVO.setAppId(json.getString("appId"));
		ruleMVO.setSts(Constants.STR_VAL_A);
		ruleMVO.setIsGlobal(isGlobal);
		ruleMVO.setTodoType(todoType);
		JSONObject confArgs = new JSONObject();
		confArgs.put("period", period);
		if (Constants.TODO_TYPE_0.equals(todoType)) {
			// 设置邮件相关的信息
			JSONObject mailInfo = new JSONObject();

			String[] mailToList = mailTo.split(Constants.MAIL_SPLIT_CHAR);

			if (!StringUtil.isBlank(mailCc)) {
				String[] mailCcList = mailCc.split(Constants.MAIL_SPLIT_CHAR);
				mailInfo.put("mailCc", mailCcList);
			}

			mailInfo.put("mailTo", mailToList);
			confArgs.put("mailInfo", mailInfo);
		} else if (Constants.TODO_TYPE_1.equals(todoType)) {
			// 设置接口相关的信息
			JSONObject urlInfo = new JSONObject();
			urlInfo.put("urlCall", json.getString("urlCall"));
			confArgs.put("urlInfo", urlInfo);
		}

		ruleMVO.setConfigArgs(confArgs.toJSONString());

		try {
			ruleMVO = alarmRuleSer.createAlarmRule(ruleMVO);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "操作成功");

		} catch (Exception e) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "创建告警规则失败");
			e.printStackTrace();
		}
		return reJson.toJSONString();
	}

	/***
	 * 创建用户默认规则
	 *
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/alarmRule/modify", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
	@ResponseBody
	public String modifyAppAlarmRule(HttpServletRequest request, ModelMap root, @RequestBody String jsonStr)
			throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");

		JSONObject reJson = new JSONObject();

		if (StringUtil.isBlank(uid)) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return reJson.toJSONString();
		}

		JSONObject json = JSON.parseObject(jsonStr);
		if (!json.containsKey("isGlobal")) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到isGlobal参数信息");
		}
		String isGlobal = json.getString("isGlobal");
		if (!json.containsKey("period")) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到告警间隔参数信息");
		}
		String period = json.getString("period");
		if (!json.containsKey("todoType")) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到操作类型参数信息");
		}
		String ruleId = json.getString("ruleId");
		if (!json.containsKey("ruleId")) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到告警规则参数信息");
		}
		String todoType = json.getString("todoType");
		String mailTo = null;
		String mailCc = null;
		if (Constants.TODO_TYPE_0.equals(todoType)) {
			if (!json.containsKey("mailTo")) {
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到邮件发送人信息");
				return reJson.toJSONString();
			}
			mailTo = json.getString("mailTo").replace("\n", "");
			if (json.containsKey("mailCc")) {
				mailCc = json.getString("mailCc").replace("\n", "");
			}
		} else if (Constants.TODO_TYPE_1.equals(todoType)) {
			if (!json.containsKey("urlCall")) {
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未找到回调接口信息");
				return reJson.toJSONString();
			}
		} else {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "未定义告警操作类型");
			return reJson.toJSONString();
		}

		AlarmRuleMVO ruleMVO = new AlarmRuleMVO();
		ruleMVO.setRuleId(ruleId);
		ruleMVO.setTodoType(todoType);
		JSONObject confArgs = new JSONObject();
		confArgs.put("period", period);
		if (Constants.TODO_TYPE_0.equals(todoType)) {
			// 设置邮件相关的信息
			JSONObject mailInfo = new JSONObject();

			String[] mailToList = mailTo.split(Constants.MAIL_SPLIT_CHAR);

			if (!StringUtil.isBlank(mailCc)) {
				String[] mailCcList = mailCc.split(Constants.MAIL_SPLIT_CHAR);
				mailInfo.put("mailCc", mailCcList);
			}

			mailInfo.put("mailTo", mailToList);
			confArgs.put("mailInfo", mailInfo);
		} else if (Constants.TODO_TYPE_1.equals(todoType)) {
			// 设置接口相关的信息
			JSONObject urlInfo = new JSONObject();
			urlInfo.put("urlCall", json.getString("urlCall"));
			confArgs.put("urlInfo", urlInfo);
		}
		ruleMVO.setConfigArgs(confArgs.toJSONString());

		try {
			ruleMVO = alarmRuleSer.modifyAlarmRule(ruleMVO);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "操作成功");

		} catch (Exception e) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "创建告警规则失败");
			e.printStackTrace();
		}
		return reJson.toJSONString();
	}

}
