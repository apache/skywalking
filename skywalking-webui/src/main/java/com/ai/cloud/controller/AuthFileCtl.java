package com.ai.cloud.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ai.cloud.util.Constants;
import com.ai.cloud.util.common.StringUtil;
import com.alibaba.fastjson.JSONObject;

@Controller
public class AuthFileCtl {

	private static Logger logger = LogManager.getLogger(AlarmRuleCtl.class);

	@RequestMapping("/exportAuth/{appCode}")
	public void exportApplicationAuthFile(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("appCode") String appCode) throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");

		JSONObject reJson = new JSONObject();

		if (StringUtil.isBlank(uid)) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return ;
		}

		String filepath = "sky-walking.auth";
		response.reset();
		response.setContentType("application/octet-stream");
		String fileName = URLDecoder.decode(filepath, "utf-8");
		java.net.URLEncoder.encode(fileName, "utf-8");
		response.addHeader("Content-Disposition",
				"attachment;" + "filename=\"" + URLEncoder.encode(fileName, "utf-8") + "\"");

		StringBuilder sb = new StringBuilder("");
		sb.append("测试魂牵梦萦魂牵梦萦" + "\r\n");
		BufferedOutputStream output = null;
		BufferedInputStream input = null;
		OutputStream os = null;
		try {
			os = response.getOutputStream();
			byte[] byt = sb.toString().getBytes();
			os.write(byt);
		} catch (Exception e) {
			logger.error("导出 {} 应用制空权文件异常", appCode);
			e.printStackTrace();
		} finally {
			os.flush();
			os.close();
			if (input != null) {
				input.close();
			}
			if (output != null) {
				output.close();
			}
		}
		return;
	}
	
	@RequestMapping("/confAuthInfo/{appCode}")
	public String confAuthInfo(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("appCode") String appCode) throws Exception {
		HttpSession session = request.getSession();
		String uid = (String) session.getAttribute("uid");

		JSONObject reJson = new JSONObject();

		if (StringUtil.isBlank(uid)) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户会话超时");
			return reJson.toJSONString();
		}
		
		return reJson.toJSONString();
	}
}
