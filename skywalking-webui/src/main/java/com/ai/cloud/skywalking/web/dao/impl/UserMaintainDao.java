package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.IUserMaintainDao;
import com.ai.cloud.skywalking.web.util.DBConnectUtil;
import com.ai.cloud.skywalking.web.vo.LoginUserInfo;
import com.ai.cloud.skywalking.web.vo.SignInUserInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Repository
public class UserMaintainDao implements IUserMaintainDao {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DBConnectUtil dbConnectUtil;

    private static Logger logger = LogManager.getLogger(UserMaintainDao.class);

    @Override
    public LoginUserInfo queryUserInfoByName(String userName) {
        logger.debug("query user name {} ", userName);
        LoginUserInfo userInfo = null;
        String sql = "select uid,user_name,password from user_info where user_name = ? ";
        try {
            PreparedStatement ps = dbConnectUtil.getConnection().prepareStatement(sql);
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
        }

        logger.info("result : {}", userInfo);
        return userInfo;
    }

    @Override
    public void addUser(final SignInUserInfo userInfo) {
        final String sql = "insert into user_info(user_name,password,role_type," +
                "create_time,sts,modify_time) values (?,?,?,?,?,?)";
        int num = -1;
        try {
            PreparedStatement pstmt = dbConnectUtil.getConnection().
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
        }
        logger.info("用户注册成功：{}", num);
        userInfo.setUid(String.valueOf(num));
    }


}
