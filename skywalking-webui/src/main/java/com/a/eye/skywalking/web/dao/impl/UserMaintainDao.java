package com.a.eye.skywalking.web.dao.impl;

import com.a.eye.skywalking.web.dao.inter.IUserMaintainDao;
import com.a.eye.skywalking.web.dto.LoginUserInfo;
import com.a.eye.skywalking.web.util.DBConnectUtil;
import com.a.eye.skywalking.web.dto.SignInUserInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.*;

@Repository
public class UserMaintainDao implements IUserMaintainDao {

    @Autowired
    private DBConnectUtil dbConnectUtil;

    private static Logger logger = LogManager.getLogger(UserMaintainDao.class);

    @Override
    public LoginUserInfo queryUserInfoByName(String userName) throws SQLException {
        logger.debug("query user name {} ", userName);
        LoginUserInfo userInfo = null;
        String sql = "select uid,user_name,password from user_info where user_name = ? ";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, userName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                userInfo = new LoginUserInfo();
                userInfo.setUid(rs.getString("uid"));
                userInfo.setUserName(rs.getString("user_name"));
                userInfo.setPassword(rs.getString("password"));
            }
        } catch (Exception e) {
            logger.error("Failed to query the user[{}]", userName, e);
        } finally {
            if (connection != null)
                connection.close();

        }

        logger.info("result : {}", userInfo);
        return userInfo;
    }

    @Override
    public void addUser(final SignInUserInfo userInfo) throws SQLException {
        final String sql = "insert into user_info(user_name,password,role_type," +
                "create_time,sts,modify_time) values (?,?,?,?,?,?)";
        int num = -1;
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement pstmt = connection.
                    prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int i = 0;
            pstmt.setString(++i, userInfo.getUserName());
            pstmt.setString(++i, userInfo.getPassword());
            pstmt.setString(++i, userInfo.getRoleType());
            pstmt.setTimestamp(++i, userInfo.getCreateTime());
            pstmt.setString(++i, userInfo.getSts());
            pstmt.setTimestamp(++i, userInfo.getModifyTime());

            pstmt.executeUpdate();
            ResultSet results = pstmt.getGeneratedKeys();
            if (results.next()) {
                num = results.getInt(1);
            }
        } catch (Exception e) {
            logger.error("Failed to register the user[{}]", userInfo.getUserName(), e);
            throw new RuntimeException("Failed to register the user " + userInfo.getUserName(), e);
        } finally {
            if (connection != null)
                connection.close();

        }
        logger.info("用户注册成功：{}", num);
        userInfo.setUid(String.valueOf(num));
    }


}
