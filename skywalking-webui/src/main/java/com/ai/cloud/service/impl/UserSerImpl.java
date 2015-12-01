package com.ai.cloud.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ai.cloud.dao.inter.IUserInfoMDAO;
import com.ai.cloud.service.inter.IUserSer;
import com.ai.cloud.vo.mvo.UserInfoMVO;

@Service
public class UserSerImpl implements IUserSer {
	
	@Autowired
	IUserInfoMDAO userInfoMDAO;
	
	@Override
	public UserInfoMVO login(UserInfoMVO userInfo){
		String userName = userInfo.getUserName();
		userInfo = userInfoMDAO.queryUserInfoByName(userName);
		return userInfo;
	}
	
}
