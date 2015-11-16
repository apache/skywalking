/**
 * 
 */
package com.ai.cloud.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ai.cloud.util.Constants;

/**
 * 未例代码
 * 
 * @author tz
 * @date 2015年11月10日 下午2:41:30
 * @version V0.3
 */
@Controller
public class HelloSkyWalkingUICtl {

	@RequestMapping(value = "")
	public String showDefaultIndexPage(ModelMap root) throws Exception {
		return "index";
	}

	@RequestMapping(value = "/index")
	public String showIndexPage(ModelMap root) throws Exception {
		return "index";
	}

	private void showIndexPage(ModelMap root, String nullStr) {
		root.put(Constants.VERSION_STR, Constants.VERSION_VAL);
	}
	
	@RequestMapping(value = "/showTraceLog")
	public String showTraceLog(ModelMap root) throws Exception {
		this.showIndexPage(root, null);
		return "traceLog";
	}
	
	@RequestMapping(value = "/login")
	public String login(ModelMap root) throws Exception {
		return "login";
	}
	
	@RequestMapping(value = "/logout")
	public String logout(ModelMap root) throws Exception {
		return "traceLog";
	}

	@RequestMapping(value = "/404")
	public String show404Page(ModelMap root) throws Exception {
		return "404";
	}

	@RequestMapping(value = "/500")
	public String show500Page(ModelMap root) throws Exception {
		return "500";
	}
}
