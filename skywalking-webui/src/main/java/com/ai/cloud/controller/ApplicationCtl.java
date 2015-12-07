package com.ai.cloud.controller;

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

import com.ai.cloud.service.inter.IApplicationSer;
import com.ai.cloud.util.Constants;
import com.ai.cloud.util.common.StringUtil;
import com.ai.cloud.vo.mvo.ApplicationInfoMVO;
import com.ai.cloud.vo.svo.ApplicationInfoSVO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 用户应用请求处理
 * 
 * @author tz
 * @date 2015年11月10日 下午2:41:30
 * @version V0.3
 */
@Controller
public class ApplicationCtl {
	
	@Autowired
	IApplicationSer applicationSer;
	
	private static Logger logger = LogManager.getLogger(ApplicationCtl.class);
	
	/***
	 * 登录后默认页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/applist")
	public String showIndexPage(HttpServletRequest request, ModelMap root) throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");
		
		if(StringUtil.isBlank(uid)){
			return "404";
		}
		
		List<ApplicationInfoMVO> appList = applicationSer.queryUserAppListByUid(uid);
		root.put("applist", appList);
		return "applist";
	}
	
	/***
	 * 创建app_code
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/appinfo/create", method = RequestMethod.POST, produces="application/json; charset=UTF-8")
	@ResponseBody
	public String createAppInfo(HttpServletRequest request, ModelMap root, @RequestBody String json) throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");
		
		JSONObject reJson = new JSONObject();
		
		if(StringUtil.isBlank(uid)){
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return reJson.toJSONString();
		}
		
		JSONObject appJson = JSON.parseObject(json);
		
		String appCode = null;
		if(appJson.containsKey("appCode")){
			appCode = appJson.getString("appCode");
		}
		if(StringUtil.isBlank(appCode)){
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "创建应用名称为空");
		}
		
		ApplicationInfoSVO appSVO = new ApplicationInfoSVO();
		appSVO.setAppCode(appCode);
		appSVO.setUid(uid);
		appSVO.setSts(Constants.STR_VAL_A);
		return applicationSer.createApplicationInfo(appSVO);
	}
	
	/***
	 * 登录后默认页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/appinfo/delete/{appId}", method = RequestMethod.POST, produces="application/json; charset=UTF-8")
	@ResponseBody
	public String deleteAppInfo(HttpServletRequest request, ModelMap root, @PathVariable("appId") String appId) throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");
		
		JSONObject reJson = new JSONObject();
		
		if(StringUtil.isBlank(uid)){
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return reJson.toJSONString();
		}
		
		if(StringUtil.isBlank(appId)){
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "删除应用编码不存在");
		}
		
		ApplicationInfoSVO appSVO = new ApplicationInfoSVO();
		appSVO.setAppId(appId);
		return applicationSer.delete(appSVO);
	}
	
}
