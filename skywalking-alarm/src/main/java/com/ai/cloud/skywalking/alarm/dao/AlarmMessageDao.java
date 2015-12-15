package com.ai.cloud.skywalking.alarm.dao;

import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.util.DBConnectUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlarmMessageDao {

    private static Logger logger = LogManager.getLogger(AlarmMessageDao.class);


    public static List<String> selectAllUserIds() throws SQLException {
        List<String> result = new ArrayList<String>();
        Connection connection = DBConnectUtil.getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT user_info.uid FROM user_info WHERE sts = ?");
            ps.setString(1, "A");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("uid"));
            }
        } catch (SQLException e) {
            logger.error("Failed to select all user info", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        return result;
    }

    public static UserInfo selectUser(String userId) throws SQLException {
        UserInfo userInfo = null;
        Connection connection = DBConnectUtil.getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT user_info.uid,user_info.user_name FROM user_info WHERE sts = ? AND uid = ?");
            ps.setString(1, "A");
            ps.setString(2, userId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            userInfo = new UserInfo(rs.getString("uid"));
            userInfo.setUserName(rs.getString("user_name"));
        } catch (SQLException e) {
            logger.error("Failed to select all user info", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        return userInfo;
    }

    public static List<AlarmRule> selectAlarmRulesByUserId(String userId) throws SQLException {
        List<AlarmRule> rules = new ArrayList<AlarmRule>();
        Connection connection = DBConnectUtil.getConnection();
        try {
            PreparedStatement ps = connection.
                    prepareStatement("SELECT alarm_rule.app_id,alarm_rule.rule_id, alarm_rule.uid,alarm_rule.is_global, alarm_rule.todo_type," +
                            " alarm_rule.config_args FROM  alarm_rule WHERE uid = ? AND sts = ?");
            ps.setString(1, userId);
            ps.setString(2, "A");
            ResultSet rs = ps.executeQuery();
            AlarmRule tmpAlarmRule = null;
            ApplicationInfo tmpApplication;
            Map<String, AlarmRule> rulesMap = new HashMap<String, AlarmRule>();
            while (rs.next()) {
                tmpAlarmRule = new AlarmRule(rs.getString("uid"), rs.getString("rule_id"));
                tmpAlarmRule.setConfigArgs(rs.getString("config_args"));
                tmpAlarmRule.setTodoType(rs.getString("todo_type"));
                if ("1".equals(rs.getString("is_global"))) {
                    rulesMap.put("*", tmpAlarmRule);
                    continue;
                }

                tmpApplication = new ApplicationInfo();
                tmpApplication.setAppId(rs.getString("app_id"));
                tmpApplication.setUId(rs.getString("uid"));
                tmpAlarmRule.getApplicationInfos().add(tmpApplication);
                rulesMap.put(rs.getString("app_id"), tmpAlarmRule);
            }

            List<ApplicationInfo> allApplication = new ArrayList<ApplicationInfo>();
            ps = connection
                    .prepareStatement("SELECT application_info.app_id, application_info.uid, app_code FROM application_info WHERE uid = ? AND sts = ?");
            ps.setString(1, userId);
            ps.setString(2, "A");
            rs = ps.executeQuery();
            ApplicationInfo applicationInfo;
            while (rs.next()) {
                applicationInfo = new ApplicationInfo();
                applicationInfo.setAppId(rs.getString("app_id"));
                applicationInfo.setUId(rs.getString("uid"));
                applicationInfo.setAppCode(rs.getString("app_code"));
                allApplication.add(applicationInfo);
            }


            for (ApplicationInfo app : allApplication) {
                tmpAlarmRule = rulesMap.get(app.getAppId());
                if (tmpAlarmRule != null) {
                    tmpAlarmRule.getApplicationInfos().get(0).setAppCode(app.getAppCode());
                } else {
                    rulesMap.get("*").getApplicationInfos().add(app);
                }
            }

            rules.addAll(rulesMap.values());
        } catch (SQLException e) {
            logger.error("Failed to query applications.", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return rules;
    }

    public static String selectAppCodeByAppId(String appId) throws SQLException {
        Connection connection = DBConnectUtil.getConnection();
        try {
            PreparedStatement ps =
                    connection.prepareStatement("SELECT app_code FROM application_info WHERE sts = ? AND  application_info.app_id = ?");
            ps.setString(1, "A");
            ps.setString(2, appId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getString("app_code");
        } catch (SQLException e) {
            throw e;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
}
