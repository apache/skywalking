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
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.ai.cloud.dao.inter.IAppAuthInfoMDAO;
import com.ai.cloud.vo.mvo.AppAuthInfoMVO;

@Repository
public class AppAuthInfoMDAO implements IAppAuthInfoMDAO {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static Logger logger = LogManager.getLogger(AppAuthInfoMDAO.class);
	
	@Override
	public AppAuthInfoMVO queryAppAuthInfo(AppAuthInfoMVO rule) {
		final AppAuthInfoMVO ruleMVO = new AppAuthInfoMVO();
		String sql = "select info_id,app_id,auth_json,create_time,sts,modify_time from app_auth_info where ";
		final Object[] params = new Object[] { rule.getInfoId() };
		jdbcTemplate.query(sql, params, new RowCallbackHandler() {
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				ruleMVO.setInfoId(rs.getString("info_id"));
				ruleMVO.setAppId(rs.getString("app_id"));
				ruleMVO.setAuthJson(rs.getString("auth_json"));
				ruleMVO.setCreateTime(rs.getTimestamp("create_time"));
				ruleMVO.setSts(rs.getString("sts"));
				ruleMVO.setModifyTime(rs.getTimestamp("modify_time"));
			}
		});
		logger.info("result : {}", ruleMVO);
		return ruleMVO;
	}

	@Override
	public AppAuthInfoMVO createAlarmRule(final AppAuthInfoMVO ruleMVO) {
		final String sql = "insert into app_auth_info (app_id,auth_json,create_time,sts,modify_time) values (?,?,sysdate(),?,sysdate())";
		KeyHolder keyHolder = new GeneratedKeyHolder();

		int count = jdbcTemplate.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				int i = 0;
				pstmt.setString(++i, ruleMVO.getAppId());
				pstmt.setString(++i, ruleMVO.getAuthJson());
				pstmt.setString(++i, ruleMVO.getSts());
				return pstmt;
			}
		}, keyHolder);
		logger.info("创建授权文件成功：{}", keyHolder.getKey().intValue());
		ruleMVO.setInfoId(keyHolder.getKey().toString());

		return ruleMVO;
	}

	@Override
	public AppAuthInfoMVO modifyAlarmRule(final AppAuthInfoMVO ruleMVO) {
		final String sql = "update app_auth_info set auth_json = ?,modify_time = sysdate() where info_id = ?";

		int count = jdbcTemplate.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				int i = 0;
				pstmt.setString(++i, ruleMVO.getAuthJson());
				pstmt.setString(++i, ruleMVO.getInfoId());
				return pstmt;
			}
		});

		return ruleMVO;
	}
	
}
