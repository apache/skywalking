package com.ai.cloud.util.view;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;

import com.ai.cloud.util.common.RequestUtil;
import com.ai.cloud.util.common.StringUtil;

/**
 * 自定义视图处理类
 * 
 * @Desc: 添加base属性，方便freemarker页面中引用
 * @Title: BaseFreeMarkerView.java
 * @author mapl
 *
 */

public class BaseFreeMarkerView extends FreeMarkerView {

	private static Logger logger = LogManager.getLogger(BaseFreeMarkerView.class);

	private static final String CONTEXT_PATH = "base";

	@Override
	protected void exposeHelpers(Map<String, Object> model, HttpServletRequest request) throws Exception {

		String base = RequestUtil.getAppWebBase(request);
		model.put(CONTEXT_PATH, base);
		
		HttpSession session = request.getSession();
		
		String isLogin = (String) session.getAttribute("isLogin");
		if(StringUtil.isBlank(isLogin)){
			isLogin = "0";
		}
		String uid = (String) session.getAttribute("uid");
		if(StringUtil.isBlank(uid)){
			uid = "-1";
		}
		String userName = (String) session.getAttribute("userName");
		if(StringUtil.isBlank(userName)){
			userName = "";
		}
		String menuList = (String) session.getAttribute("menuList");
		if(StringUtil.isBlank(menuList)){
			menuList = "";
		}
		
		model.put("userInfo", "{'isLogin':'" + isLogin + "','uid':'" + uid + "','userName':'" + userName + "','menuList':'" + menuList + "'}");
		
		super.exposeHelpers(model, request);
	}

}
