package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.IAlarmRuleMaintainDao;
import com.ai.cloud.skywalking.web.entity.AlarmRule;
import com.ai.cloud.skywalking.web.util.DBConnectUtil;
import com.ai.cloud.vo.svo.ConfigArgs;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.*;

/**
 * Created by xin on 16-3-27.
 */
@Repository
public class AlarmRuleMaintainDao implements IAlarmRuleMaintainDao {

    @Autowired
    private DBConnectUtil dbConnectUtil;

    private static Logger logger = LogManager.getLogger(AlarmRuleMaintainDao.class);

    @Override
    public AlarmRule queryGlobalAlarmRule(String userId) throws SQLException {
        AlarmRule rule = null;
        String sql = "select rule_id,app_id,uid,config_args,is_global,todo_type,create_time,sts,modify_time from alarm_rule a where a.uid = ? and a.is_global = '1' and a.sts='A'";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            rule = fetchAlarmRuleIfNecessary(rs);
        } catch (Exception e) {
            logger.error("Failed to query the user[{}]", userId, e);
        } finally {
            if (connection != null)
                connection.close();
        }
        return rule;
    }

    @Override
    public void saveAlarmRule(final AlarmRule rule) throws SQLException {
        final String sql = "insert into alarm_rule (app_id,uid,config_args,is_global,todo_type,create_time,sts,modify_time) values (?,?,?,?,?,?,?,?)";
        int num = -1;
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int i = 0;
            pstmt.setString(++i, rule.getAppId());
            pstmt.setString(++i, rule.getUid());
            pstmt.setString(++i, new Gson().toJson(rule.getConfigArgs()));
            pstmt.setString(++i, rule.getIsGlobal());
            pstmt.setString(++i, rule.getTodoType());
            pstmt.setTimestamp(++i, rule.getCreateTime());
            pstmt.setString(++i, rule.getSts());
            pstmt.setTimestamp(++i, rule.getModifyTime());
            pstmt.executeUpdate();
            ResultSet results = pstmt.getGeneratedKeys();
            if (results.next()) {
                num = results.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save Alarm Rules", e);
        } finally {
            if (connection != null)
                connection.close();
        }
        logger.info("创建应用成功：{}", num);
        rule.setRuleId(num + "");
    }

    @Override
    public void updateAlarmRule(AlarmRule alarmRule) throws SQLException {
        final String sql = "update alarm_rule set config_args = ?,todo_type = ?, sts = ? where rule_id = ?";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            int i = 0;
            pstmt.setString(++i, new Gson().toJson(alarmRule.getConfigArgs()));
            pstmt.setString(++i, alarmRule.getTodoType());
            pstmt.setString(++i, alarmRule.getSts());
            pstmt.setString(++i, alarmRule.getRuleId());

            pstmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update Alarm rule", e);
        } finally {
            if (connection != null)
                connection.close();
        }
    }

    @Override
    public AlarmRule queryAlarmRule(String userId, String applicationId) throws SQLException {
        AlarmRule rule = null;
        String sql = "select rule_id,app_id,uid,config_args,is_global,todo_type,create_time,sts,modify_time from alarm_rule a where a.uid = ? and a.app_id=? and a.is_global = '0' and a.sts='A'";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, userId);
            ps.setString(2, applicationId);
            ResultSet rs = ps.executeQuery();
            rule = fetchAlarmRuleIfNecessary(rs);
        } catch (Exception e) {
            logger.error("Failed to query the user[{}]", userId, e);
        } finally {
            if (connection != null)
                connection.close();
        }
        return rule;
    }

    private AlarmRule fetchAlarmRuleIfNecessary(ResultSet rs) throws SQLException {
        AlarmRule rule = null;
        if (rs.next()) {
            rule = new AlarmRule();
            rule.setRuleId(rs.getString("rule_id"));
            rule.setAppId(rs.getString("app_id"));
            rule.setUid(rs.getString("uid"));
            rule.setConfigArgs(new Gson().fromJson(rs.getString("config_args"), ConfigArgs.class));
            rule.setIsGlobal(rs.getString("is_global"));
            rule.setTodoType(rs.getString("todo_type"));
            rule.setCreateTime(rs.getTimestamp("create_time"));
            rule.setSts(rs.getString("sts"));
            rule.setModifyTime(rs.getTimestamp("modify_time"));
        }
        return rule;
    }
}
