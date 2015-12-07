package com.ai.cloud.dao.inter;

import com.ai.cloud.vo.mvo.UserInfoMVO;

public interface IUserInfoMDAO {
	
	public UserInfoMVO queryUserInfoByName(String userName);
	
	public UserInfoMVO addUser(final UserInfoMVO userInfo);
	
}
