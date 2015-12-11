package com.ai.cloud.skywalking.alarm.dao;

import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.util.DBConnectUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AlarmMessageDao {

    private static Logger logger = LogManager.getLogger(AlarmMessageDao.class);


    public static List<String> selectAllUserIds() {
        List<String> result = new ArrayList<String>();
        try {
            PreparedStatement ps = DBConnectUtil.getConnection().prepareStatement("SELECT user_info.uid FROM user_info WHERE sts = ?");
            ps.setString(1, "A");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("uid"));
            }
        } catch (SQLException e) {
            logger.error("Failed to select all user info", e);
        }

        return result;
    }

    public static int selectUserCount() {
        try {
            PreparedStatement ps = DBConnectUtil.getConnection().prepareStatement("SELECT count(user_info.uid) as totalNumber FROM user_info WHERE sts = ?");
            ps.setString(1, "A");
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt("totalNumber");
        } catch (SQLException e) {
            logger.error("Failed to select all user info", e);
        }

        return 0;
    }

    public static UserInfo selectUser(String userId) {
        UserInfo userInfo = null;
        try {
            PreparedStatement ps = DBConnectUtil.getConnection().prepareStatement("SELECT user_info.uid,user_info.user_name FROM user_info WHERE sts = ? AND uid = ?");
            ps.setString(1, "A");
            ps.setString(2, userId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            userInfo = new UserInfo(rs.getString("uid"));
            userInfo.setUserName(rs.getString("user_name"));
        } catch (SQLException e) {
            logger.error("Failed to select all user info", e);
        }

        return userInfo;
    }

    public static List<AlarmRule> selectAlarmRulesByUserId(String userId) {
        List<AlarmRule> rules = new ArrayList<AlarmRule>();
        List<ApplicationInfo> selfDefineApplications = new ArrayList<ApplicationInfo>();
        try {
            PreparedStatement ps = DBConnectUtil.getConnection().prepareStatement("SELECT alarm_rule.app_id,alarm_rule.rule_id, alarm_rule.uid,alarm_rule.is_global, alarm_rule.todo_type," +
                    " alarm_rule.config_args FROM  alarm_rule WHERE uid = ? AND sts = ?");
            ps.setString(1, userId);
            ps.setString(2, "A");
            ResultSet rs = ps.executeQuery();
            AlarmRule globalRules = null;
            AlarmRule tmpAlarmRule = null;
            ApplicationInfo tmpApplication;
            while (rs.next()) {
                if ("1".equals(rs.getString("is_global"))) {
                    globalRules = new AlarmRule(rs.getString("uid"), rs.getString("rule_id"));
                    globalRules.setConfigArgs(rs.getString("config_args"));
                    globalRules.setTodoType(rs.getString("todo_type"));
                    globalRules.setGlobal(true);
                    continue;
                } else {
                    tmpAlarmRule = new AlarmRule(rs.getString("uid"), rs.getString("rule_id"));
                    globalRules.setConfigArgs(rs.getString("config_args"));
                    globalRules.setTodoType(rs.getString("todo_type"));
                    // 自定义规则的Application
                    tmpApplication = new ApplicationInfo();
                    tmpApplication.setAppId(rs.getString("app_id"));
                    tmpApplication.setUId(rs.getString("uid"));
                    selfDefineApplications.add(tmpApplication);

                    tmpAlarmRule.getApplicationInfos().add(tmpApplication);
                    rules.add(tmpAlarmRule);
                }
            }

            if (globalRules == null) {
                //
                throw new IllegalArgumentException("Can not found the global config");
            }

            List<ApplicationInfo> allApplication = new ArrayList<ApplicationInfo>();
            ps = DBConnectUtil.getConnection()
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

            allApplication.removeAll(selfDefineApplications);
            globalRules.getApplicationInfos().addAll(allApplication);
            rules.add(globalRules);
        } catch (SQLException e) {
            logger.error("Failed to query applications.", e);
        }
        return rules;
    }
}
