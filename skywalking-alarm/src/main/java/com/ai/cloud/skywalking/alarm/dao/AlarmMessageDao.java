package com.ai.cloud.skywalking.alarm.dao;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AlarmMessageDao {

    private static Logger logger = LogManager.getLogger(AlarmMessageDao.class);
    private static Connection con;

    static {
        try {
            Class.forName(Config.DB.DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            logger.error("Failed to found DB driver class.", e);
            System.exit(-1);
        }

        try {
            con = DriverManager.getConnection(Config.DB.URL, Config.DB.USER_NAME, Config.DB.PASSWORD);
        } catch (SQLException e) {
            logger.error("Failed to connect DB", e);
            System.exit(-1);
        }
    }

    public static List<UserInfo> selectAllUserInfo() {
        List<UserInfo> result = new ArrayList<UserInfo>();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT user_info.uid FROM user_info WHERE sts = ?");
            ps.setString(1, "A");
            ResultSet rs = ps.executeQuery();
            UserInfo userInfo;
            while (rs.next()) {
                userInfo = new UserInfo(rs.getString("uid"));
                result.add(userInfo);
            }
        } catch (SQLException e) {
            logger.error("Failed to select all user info", e);
        }

        return result;
    }

    public static int selectUserCount() {
        try {
            PreparedStatement ps = con.prepareStatement("SELECT count(user_info.uid) as totalNumber FROM user_info WHERE sts = ?");
            ps.setString(1, "A");
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt("totalNumber");
        } catch (SQLException e) {
            logger.error("Failed to select all user info", e);
        }

        return 0;
    }

    public static List<ApplicationInfo> selectAllApplicationsByUserId(String userId) {
        List<ApplicationInfo> result = new ArrayList<ApplicationInfo>();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT alarm_rule.app_id, alarm_rule.uid,alarm_rule.is_global, alarm_rule.todo_type," +
                    " alarm_rule.config_args FROM  alarm_rule WHERE uid = ? AND sts = ?");
            ps.setString(1, userId);
            ps.setString(2, "A");
            ResultSet rs = ps.executeQuery();
            ApplicationInfo globalConfig = null;
            ApplicationInfo tmpApplication;
            while (rs.next()) {
                if ("1".equals(rs.getString("is_global"))) {
                    globalConfig = new ApplicationInfo();
                    globalConfig.setConfigArgs(rs.getString("config_args"));
                    continue;
                }
                tmpApplication = new ApplicationInfo();
                tmpApplication.setAppId(rs.getString("app_id"));
                tmpApplication.setUId(rs.getString("uid"));
                tmpApplication.setConfigArgs(rs.getString("config_args"));
                tmpApplication.setToDoType(rs.getString("todo_type"));
                result.add(tmpApplication);
            }

            if (globalConfig == null) {
                //
                throw new IllegalArgumentException("Can not found the global config");
            }

            List<ApplicationInfo> allApplication = new ArrayList<ApplicationInfo>();
            ps = con.prepareStatement("SELECT application_info.app_id, application_info.uid FROM application_info WHERE uid = ? AND sts = ?");
            ps.setString(1, userId);
            ps.setString(2, "A");
            rs = ps.executeQuery();
            ApplicationInfo applicationInfo;
            while (rs.next()) {
                applicationInfo = new ApplicationInfo();
                applicationInfo.setAppId(rs.getString("app_id"));
                applicationInfo.setUId(rs.getString("uid"));
                allApplication.add(applicationInfo);
            }

            allApplication.removeAll(result);

            for (ApplicationInfo app : allApplication) {
                app.setConfigArgs(globalConfig.getConfigArgs());
                app.setToDoType(globalConfig.getToDoType());
                result.add(app);
            }

        } catch (SQLException e) {
            logger.error("Failed to query applications.", e);
        }
        return result;
    }
}
