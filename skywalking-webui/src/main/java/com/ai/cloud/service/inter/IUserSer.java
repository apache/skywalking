package com.ai.cloud.service.inter;

import com.ai.cloud.vo.mvo.UserInfoMVO;
import com.alibaba.fastjson.JSONObject;

public interface IUserSer {
	
	public UserInfoMVO login(UserInfoMVO userInfo);
	
	public JSONObject regist(UserInfoMVO userInfo);
	
}
