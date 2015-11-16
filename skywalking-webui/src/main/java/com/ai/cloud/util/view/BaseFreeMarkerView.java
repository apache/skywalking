package com.ai.cloud.util.view;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;

import com.ai.cloud.util.common.RequestUtil;

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
		
		model.put("userInfo", "{'isLogin':'0','uid':'10000','userName':'tanzhen'}");
		
		model.put("isLogin", "{'isLogin':'0'}");
		
		model.put("menuInfo", "{'isLogin':'0','uid':'10000','menuList':'tanzhen'}");
		
		super.exposeHelpers(model, request);
	}

}
