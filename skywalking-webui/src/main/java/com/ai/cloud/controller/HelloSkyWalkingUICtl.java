/**
 * 
 */
package com.ai.cloud.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ai.cloud.util.Constants;

/**
 * 首面请求处理
 * 
 * @author tz
 * @date 2015年11月10日 下午2:41:30
 * @version V0.3
 */
@Controller
public class HelloSkyWalkingUICtl {
	
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
	@RequestMapping(value = "/{traceId}")
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
	@RequestMapping(value = "/showTraceLog")
	public String showTraceLog(ModelMap root) throws Exception {
		this.showIndexPage(root, null);
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
	public String login(ModelMap root) throws Exception {
		return "login";
	}

	/***
	 * 退出
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/logout")
	public String logout(ModelMap root) throws Exception {
		return "traceLog";
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
