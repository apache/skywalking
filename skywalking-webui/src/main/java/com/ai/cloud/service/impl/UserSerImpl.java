package com.ai.cloud.service.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ai.cloud.dao.inter.IUserInfoMDAO;
import com.ai.cloud.service.inter.IUserSer;
import com.ai.cloud.util.Constants;
import com.ai.cloud.util.common.StringUtil;
import com.ai.cloud.vo.mvo.UserInfoMVO;
import com.alibaba.fastjson.JSONObject;

@Service
public class UserSerImpl implements IUserSer {

	@Autowired
	IUserInfoMDAO userInfoMDAO;
	
	private static Logger logger = LogManager.getLogger(UserSerImpl.class);

	@Override
	public UserInfoMVO login(UserInfoMVO userInfo) {
		String userName = userInfo.getUserName();
		userInfo = userInfoMDAO.queryUserInfoByName(userName);
		return userInfo;
	}

	@Override
	public JSONObject regist(UserInfoMVO userInfo) {
		String userName = userInfo.getUserName();
		JSONObject reJson = new JSONObject();
		UserInfoMVO reUserInfo = userInfoMDAO.queryUserInfoByName(userName);
		if (reUserInfo != null && !StringUtil.isBlank(reUserInfo.getUid())) {
			reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
			reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户已经存在");
			return reJson;
		} else {
			try {
				userInfoMDAO.addUser(userInfo);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_OK);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "注册成功");
			} catch (Exception e) {
				reJson.put(Constants.JSON_RESULT_KEY_RESULT, Constants.JSON_RESULT_KEY_RESULT_FAIL);
				reJson.put(Constants.JSON_RESULT_KEY_RESULT_MSG, "用户注册失败");
				e.printStackTrace();
				return reJson;
			}
		}

		return reJson;
	}

}
