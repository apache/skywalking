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

import com.ai.cloud.dao.inter.IAlarmRuleMDAO;
import com.ai.cloud.vo.mvo.AlarmRuleMVO;

@Repository
public class AlarmRuleMDAO implements IAlarmRuleMDAO {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static Logger logger = LogManager.getLogger(AlarmRuleMDAO.class);

	@Override
	public AlarmRuleMVO queryUserDefaultAlarmRule(AlarmRuleMVO rule) {
		final AlarmRuleMVO ruleMVO = new AlarmRuleMVO();
		String sql = "select rule_id,app_id,uid,config_args,is_global,todo_type,create_time,sts,modify_time from alarm_rule a where a.uid = ? and a.is_global = '1' and a.sts='A'";
		final Object[] params = new Object[] { rule.getUid() };
		jdbcTemplate.query(sql, params, new RowCallbackHandler() {
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				ruleMVO.setRuleId(rs.getString("rule_id"));
				ruleMVO.setAppId(rs.getString("app_id"));
				ruleMVO.setUid(rs.getString("uid"));
				ruleMVO.setConfigArgs(rs.getString("config_args"));
				ruleMVO.setIsGlobal(rs.getString("is_global"));
				ruleMVO.setTodoType(rs.getString("todo_type"));
				ruleMVO.setCreateTime(rs.getTimestamp("create_time"));
				ruleMVO.setSts(rs.getString("sts"));
				ruleMVO.setModifyTime(rs.getTimestamp("modify_time"));
			}
		});
		logger.info("result : {}", ruleMVO);
		return ruleMVO;
	}

	@Override
	public AlarmRuleMVO queryAppAlarmRule(AlarmRuleMVO rule) {
		final AlarmRuleMVO ruleMVO = new AlarmRuleMVO();
		String sql = "select rule_id,app_id,uid,config_args,is_global,todo_type,create_time,sts,modify_time from alarm_rule a where a.uid = ? and app_id = ? and a.is_global = '0' and a.sts='A'";
		final Object[] params = new Object[] { rule.getUid(), rule.getAppId() };
		jdbcTemplate.query(sql, params, new RowCallbackHandler() {
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				ruleMVO.setRuleId(rs.getString("rule_id"));
				ruleMVO.setAppId(rs.getString("app_id"));
				ruleMVO.setUid(rs.getString("uid"));
				ruleMVO.setConfigArgs(rs.getString("config_args"));
				ruleMVO.setIsGlobal(rs.getString("is_global"));
				ruleMVO.setTodoType(rs.getString("todo_type"));
				ruleMVO.setCreateTime(rs.getTimestamp("create_time"));
				ruleMVO.setSts(rs.getString("sts"));
				ruleMVO.setModifyTime(rs.getTimestamp("modify_time"));
			}
		});
		logger.info("result : {}", ruleMVO);
		return ruleMVO;
	}

	@Override
	public AlarmRuleMVO createAlarmRule(final AlarmRuleMVO ruleMVO) {
		final String sql = "insert into alarm_rule (app_id,uid,config_args,is_global,todo_type,create_time,sts,modify_time) values (?,?,?,?,?,sysdate(),?,sysdate())";
		KeyHolder keyHolder = new GeneratedKeyHolder();

		int count = jdbcTemplate.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				int i = 0;
				pstmt.setString(++i, ruleMVO.getAppId());
				pstmt.setString(++i, ruleMVO.getUid());
				pstmt.setString(++i, ruleMVO.getConfigArgs());
				pstmt.setString(++i, ruleMVO.getIsGlobal());
				pstmt.setString(++i, ruleMVO.getTodoType());
				pstmt.setString(++i, ruleMVO.getSts());
				return pstmt;
			}
		}, keyHolder);
		logger.info("创建应用成功：{}", keyHolder.getKey().intValue());
		ruleMVO.setRuleId(keyHolder.getKey().toString());

		return ruleMVO;
	}

	@Override
	public AlarmRuleMVO modifyAlarmRule(final AlarmRuleMVO ruleMVO) {
		final String sql = "update alarm_rule set config_args = ?,todo_type = ? where rule_id = ?";

		int count = jdbcTemplate.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				int i = 0;
				pstmt.setString(++i, ruleMVO.getConfigArgs());
				pstmt.setString(++i, ruleMVO.getTodoType());
				pstmt.setString(++i, ruleMVO.getRuleId());
				return pstmt;
			}
		});

		return ruleMVO;
	}

}
