package com.a.eye.skywalking.web.dao.inter;

import com.a.eye.skywalking.web.dto.LoginUserInfo;
import com.a.eye.skywalking.web.dto.SignInUserInfo;

import java.sql.SQLException;

/**
 * Created by xin on 16-3-25.
 */
public interface IUserMaintainDao {
    LoginUserInfo queryUserInfoByName(String userName) throws SQLException;

    void addUser(SignInUserInfo userInfo) throws SQLException;
}
