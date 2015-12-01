package com.ai.cloud.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import com.ai.cloud.dao.inter.IUserInfoMDAO;
import com.ai.cloud.vo.mvo.UserInfoMVO;

@Repository
public class UserInfoMDAO implements IUserInfoMDAO {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	private static Logger logger = LogManager.getLogger(UserInfoMDAO.class);

	@Override
	public UserInfoMVO queryUserInfoByName(String userName) {
		final UserInfoMVO userInfo = new UserInfoMVO();
		String sql = "select uid,user_name,password from user_info where user_name = ? ";
		final Object[] params = new Object[] { userName };
		jdbcTemplate.query(sql, params, new RowCallbackHandler() {
			@Override
            public void processRow(ResultSet rs) throws SQLException {
				userInfo.setUid(rs.getString("uid"));
				userInfo.setUserName(rs.getString("user_name"));
				userInfo.setPassword(rs.getString("password"));
            }
		});
		logger.info("result : {}", userInfo);
		return userInfo;
	}

}
