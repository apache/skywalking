/**
 * 
 */
package com.ai.cloud.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ai.cloud.service.inter.IQueryTraceLogSer;
import com.ai.cloud.service.inter.IUserSer;
import com.ai.cloud.util.Constants;
import com.ai.cloud.vo.mvo.MenuInfoMVO;
import com.ai.cloud.vo.mvo.TraceLogEntry;
import com.ai.cloud.vo.mvo.UserInfoMVO;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 首面请求处理
 * 
 * @author tz
 * @date 2015年11月10日 下午2:41:30
 * @version V0.3
 */
@Controller
public class HelloSkyWalkingUICtl {

	@Autowired
	IQueryTraceLogSer traceLogSer;
	
	@Autowired
	IUserSer userSer;

	private static Logger logger = LogManager.getLogger(HelloSkyWalkingUICtl.class);

	/***
	 * 默认首页
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "")
	public String showDefaultIndexPage(ModelMap root) throws Exception {
		showIndexPage(root, null);
		return "index";
	}

	/***
	 * 默认首页
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/index")
	public String showIndexPage(ModelMap root) throws Exception {
		showIndexPage(root, null);
		return "index";
	}

	/***
	 * 处理直接查看指定traceId首页
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/{traceId:.+}")
	public String showIndexPageWithTraceId(ModelMap root, @PathVariable("traceId") String traceId) throws Exception {
		showIndexPage(root, null);
		root.put("traceId", traceId);
		return "index";
	}

	private void showIndexPage(ModelMap root, String nullStr) {
		root.put(Constants.VERSION_STR, Constants.VERSION_VAL);
	}

	/***
	 * 调度链路日志页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/showTraceLog/{traceId:.+}")
	public String showTraceLog(ModelMap root, @PathVariable("traceId") String traceId) throws Exception {
//		traceId = "bcb759bc12db474aa54bc4bea605cb81123";
		Map<String, TraceLogEntry> traceLogMap = traceLogSer.queryLogByTraceId(traceId);

		if (traceLogMap != null && traceLogMap.size() > 0) {
			List<TraceLogEntry> valueList = new ArrayList<TraceLogEntry>();
			valueList.addAll(traceLogMap.values());
			final List<Long> endTime = new ArrayList<Long>();
			endTime.add(0, valueList.get(0).getEndDate());
			Collections.sort(valueList, new Comparator<TraceLogEntry>() {
				@Override
				public int compare(TraceLogEntry arg0, TraceLogEntry arg1) {
					/** 顺道取出日志最大的结束时间 */
					if (endTime.get(0) < arg0.getEndDate()) {
						endTime.set(0, arg0.getEndDate());
					}
					if (endTime.get(0) < arg1.getEndDate()) {
						endTime.set(0, arg1.getEndDate());
					}
					return arg0.getColId().compareTo(arg1.getColId());
				}
			});
			long beginTime = valueList.get(0).getStartDate();
			root.put("traceId", traceId);
			root.put("valueList", valueList);
			root.put("spanTypeMap", Constants.SPAN_TYPE_MAP);
			root.put("statusCodeMap", Constants.STATUS_CODE_MAP);
			root.put("beginTime", beginTime);
			root.put("endTime", endTime.get(0));
		}
		return "traceLog";
	}

	/***
	 * 登录页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/login")
	public String loginPage(ModelMap root) throws Exception {
		return "login";
	}
	
	/***
	 * 登录页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	
	@RequestMapping(value = "/login/{userName}/{password}", method = RequestMethod.POST, produces="application/json; charset=UTF-8")
	@ResponseBody
	public String loginAction(HttpServletRequest request, ModelMap root, @PathVariable("userName") String userName, @PathVariable("password") String password) throws Exception {
		UserInfoMVO userInfo = new UserInfoMVO();
		userInfo.setUserName(userName);
		userInfo.setPassword(password);
		UserInfoMVO reUserInfo = userSer.login(userInfo);
		JSONObject json = new JSONObject();
		if(reUserInfo != null && password.equals(reUserInfo.getPassword())){
			json.put("result", "OK");
			json.put("msg", "登录成功");
			
			HttpSession session = request.getSession();
			session.setAttribute("isLogin", "1");
			session.setAttribute("uid", reUserInfo.getUid());
			session.setAttribute("userName", reUserInfo.getUserName());
			
			JSONArray menuArr = new JSONArray();
			List<MenuInfoMVO> menuList = new ArrayList<MenuInfoMVO>();
			menuList.add(new MenuInfoMVO("1", "应用配置", "applist"));
			menuArr.addAll(menuList);
			session.setAttribute("menuList", menuArr.toJSONString());
		}else{
			json.put("result", "FAIL");
			json.put("msg", "用户名或者密码错误");
		}
		return json.toJSONString();
	}
	
	/***
	 * 登录页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/regist")
	public String registPage(ModelMap root) throws Exception {
		return "regist";
	}
	
	/***
	 * 登录页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	
	@RequestMapping(value = "/regist/{userName}/{password}", method = RequestMethod.POST, produces="application/json; charset=UTF-8")
	@ResponseBody
	public String registAction(HttpServletRequest request, ModelMap root, @PathVariable("userName") String userName, @PathVariable("password") String password) throws Exception {
		UserInfoMVO userInfo = new UserInfoMVO();
		userInfo.setUserName(userName);
		userInfo.setPassword(password);
		userInfo.setRoleType(Constants.ROLE_TYPE_USER);
		userInfo.setSts(Constants.STR_VAL_A);
		JSONObject reUserInfo = userSer.regist(userInfo);
		return reUserInfo.toJSONString();
	}

	/***
	 * 退出
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/logout")
	@ResponseBody
	public String logout(HttpServletRequest request, ModelMap root) throws Exception {
		HttpSession session = request.getSession();
		session.removeAttribute("isLogin");
		session.removeAttribute("uid");
		session.removeAttribute("userName");
		session.removeAttribute("menuList");
		JSONObject json = new JSONObject();
		json.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
		json.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "退出成功");
		return json.toJSONString();
	}

	/***
	 * 404错误日志
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/404")
	public String show404Page(ModelMap root) throws Exception {
		return "404";
	}

	/***
	 * 500错误日志
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/500")
	public String show500Page(ModelMap root) throws Exception {
		return "500";
	}
}
