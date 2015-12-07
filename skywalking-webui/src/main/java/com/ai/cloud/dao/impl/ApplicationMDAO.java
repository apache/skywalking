package com.ai.cloud.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

import com.ai.cloud.dao.inter.IApplicationMDAO;
import com.ai.cloud.vo.mvo.ApplicationInfoMVO;
import com.ai.cloud.vo.svo.ApplicationInfoSVO;

@Repository
public class ApplicationMDAO implements IApplicationMDAO {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static Logger logger = LogManager.getLogger(ApplicationMDAO.class);

	@Override
	public List<ApplicationInfoMVO> queryAppListByUid(String uid) {
		String sqlQuery = "select a.app_id,a.uid,a.app_code,a.create_time,a.sts from application_info a where a.uid = ? and a.sts= 'A'";
		final Object[] params = new Object[] { uid };
		final List<ApplicationInfoMVO> appList = new ArrayList<ApplicationInfoMVO>();
		jdbcTemplate.query(sqlQuery, params, new RowCallbackHandler() { // editing
			public void processRow(ResultSet rs) throws SQLException {
				ApplicationInfoMVO mvo = new ApplicationInfoMVO();
				mvo.setAppId(rs.getString("app_id"));
				mvo.setUid(rs.getString("uid"));
				mvo.setAppCode(rs.getString("app_code"));
				mvo.setCreateTime(rs.getTimestamp("create_time"));
				mvo.setSts(rs.getString("sts"));
				appList.add(mvo);
			}
		});

		return appList;
	}

	@Override
	public ApplicationInfoSVO addApplicationInfo(final ApplicationInfoSVO appSVO) {
		final String sql = "insert into application_info(uid,app_code,create_time,sts) values(?,?,sysdate(),?)";
		KeyHolder keyHolder = new GeneratedKeyHolder();

		int count = jdbcTemplate.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				int i = 0;
				pstmt.setString(++i, appSVO.getUid());
				pstmt.setString(++i, appSVO.getAppCode());
				pstmt.setString(++i, appSVO.getSts());
				return pstmt;
			}
		}, keyHolder);
		logger.info("创建应用成功：{}", keyHolder.getKey().intValue());
		appSVO.setAppId(keyHolder.getKey().toString());

		return appSVO;
	}

	@Override
	public void deleteAppInfoById(final ApplicationInfoSVO appSVO) {
		String sql = "update application_info set sts = 'P' where app_id = ?";
		int count = jdbcTemplate.update(sql, new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement pstmt) throws SQLException {
				pstmt.setString(1, appSVO.getAppId());
			}
		});
	}

	@Override
	public List<ApplicationInfoMVO> queryUserAppListByAppCode(ApplicationInfoSVO appSVO) {
		String sqlQuery = "select a.app_id,a.uid,a.app_code,a.create_time,a.sts from application_info a where a.app_code = ? and a.uid= ? and a.sts= 'A'";
		final Object[] params = new Object[] { appSVO.getAppCode(), appSVO.getUid() };
		final List<ApplicationInfoMVO> appList = new ArrayList<ApplicationInfoMVO>();
		jdbcTemplate.query(sqlQuery, params, new RowCallbackHandler() { // editing
			public void processRow(ResultSet rs) throws SQLException {
				ApplicationInfoMVO mvo = new ApplicationInfoMVO();
				mvo.setAppId(rs.getString("app_id"));
				mvo.setUid(rs.getString("uid"));
				mvo.setAppCode(rs.getString("app_code"));
				mvo.setCreateTime(rs.getTimestamp("create_time"));
				mvo.setSts(rs.getString("sts"));
				appList.add(mvo);
			}
		});

		return appList;
	}

}
