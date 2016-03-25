package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.vo.LoginUserInfo;
import com.ai.cloud.skywalking.web.vo.SignInUserInfo;

/**
 * Created by xin on 16-3-25.
 */
public interface IUserMaintainDao {
    LoginUserInfo queryUserInfoByName(String userName);

    void addUser(SignInUserInfo userInfo);
}
