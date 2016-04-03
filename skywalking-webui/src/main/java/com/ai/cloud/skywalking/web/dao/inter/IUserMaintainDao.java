package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.bo.LoginUserInfo;
import com.ai.cloud.skywalking.web.bo.SignInUserInfo;

import java.sql.SQLException;

/**
 * Created by xin on 16-3-25.
 */
public interface IUserMaintainDao {
    LoginUserInfo queryUserInfoByName(String userName) throws SQLException;

    void addUser(SignInUserInfo userInfo) throws SQLException;
}
