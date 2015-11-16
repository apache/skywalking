/**
 * 
 */
package com.ai.cloud.util.common;

import javax.servlet.http.HttpServletRequest;

/**
 * 
 * @author tz
 * @date 2015年11月16日 上午11:18:54
 * @version V0.1
 */
public class RequestUtil {
	/***
	 * 获取web应用根路径
	 * @param request
	 * @return
	 */
	public static String getAppWebBase(HttpServletRequest request) {
		String path = request.getContextPath();
		String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + path
				+ "/";
		return base;
	}
}
