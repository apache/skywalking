package com.ai.cloud.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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

	@Override
	public UserInfoMVO addUser(final UserInfoMVO userInfo) {
		final String sql = "insert into user_info(user_name,password,role_type,create_time,sts,modify_time) values (?,?,?,sysdate(),?,sysdate())";
		KeyHolder keyHolder = new GeneratedKeyHolder();

		int count = jdbcTemplate.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				int i = 0;
				pstmt.setString(++i, userInfo.getUserName());
				pstmt.setString(++i, userInfo.getPassword());
				pstmt.setString(++i, userInfo.getRoleType());
				pstmt.setString(++i, userInfo.getSts());
				return pstmt;
			}
		}, keyHolder);
		logger.info("用户注册成功：{}", keyHolder.getKey().intValue());

		userInfo.setUid(keyHolder.getKey().toString());
		return userInfo;
	}

}
