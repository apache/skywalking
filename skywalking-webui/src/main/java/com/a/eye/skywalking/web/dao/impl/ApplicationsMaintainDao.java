package com.a.eye.skywalking.web.dao.impl;

import com.a.eye.skywalking.web.dao.inter.IApplicationsMaintainDao;
import com.a.eye.skywalking.web.dto.ApplicationInfo;
import com.a.eye.skywalking.web.entity.Application;
import com.a.eye.skywalking.web.util.DBConnectUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-3-28.
 */
@Repository
public class ApplicationsMaintainDao implements IApplicationsMaintainDao {

    private Logger logger = LogManager.getLogger(ApplicationsMaintainDao.class);

    @Autowired
    private DBConnectUtil dbConnectUtil;

    @Override
    public ApplicationInfo loadApplication(String applicationId, String uid) throws SQLException {
        String sqlQuery = "select a.app_id,a.uid,a.app_code,a.create_time,a.sts,a.app_desc,a.update_time from application_info a where a.app_id = ? and a.uid = ? and a.sts= 'A'";
        Connection connection = dbConnectUtil.getConnection();
        ApplicationInfo application = null;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery);
            preparedStatement.setString(1, applicationId);
            preparedStatement.setString(2, uid);

            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                application = new ApplicationInfo(applicationId);
                application.setUId(resultSet.getString("uid"));
                application.setAppCode(resultSet.getString("app_code"));
                application.setCreateTime(resultSet.getTimestamp("create_time"));
                application.setSts(resultSet.getString("sts"));
                application.setAppDesc(resultSet.getString("app_desc"));
                application.setUpdateTime(resultSet.getTimestamp("update_time"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load the application[" + applicationId + "] information");
        } finally {
            if (connection != null)
                connection.close();
        }
        return application;
    }

    @Override
    public void saveApplication(ApplicationInfo application) throws SQLException {
        final String sql = "insert into application_info(uid,app_code,create_time,sts,update_time,app_desc) values(?,?,?,?,?,?)";
        Connection connection = dbConnectUtil.getConnection();
        int key = -1;
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int i = 0;
            pstmt.setString(++i, application.getUId());
            pstmt.setString(++i, application.getAppCode());
            pstmt.setTimestamp(++i, application.getCreateTime());
            pstmt.setString(++i, application.getSts());
            pstmt.setTimestamp(++i, application.getUpdateTime());
            pstmt.setString(++i, application.getAppDesc());

            pstmt.executeUpdate();
            ResultSet results = pstmt.getGeneratedKeys();
            if (results.next()) {
                key = results.getInt(1);
            }

        } catch (Exception e) {
            logger.error("Failed to save the application[{}]", application, e);
            throw new RuntimeException("Failed to save application", e);
        } finally {
            if (connection != null)
                connection.close();
        }
        logger.info("创建应用成功：{}", key);

        application.setAppId(key + "");
    }

    @Override
    public void modifyApplication(Application application) throws SQLException {
        String sqlQuery = "update application_info set app_desc=?, update_time=?  where app_id=? and uid = ? and sts= 'A'";
        Connection connection = dbConnectUtil.getConnection();

        try {
            PreparedStatement pstmt = connection.prepareStatement(sqlQuery);
            pstmt.setString(1, application.getAppDesc());
            pstmt.setTimestamp(2, application.getUpdateTime());
            pstmt.setString(3, application.getAppId());
            pstmt.setString(4, application.getUId());

            pstmt.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to load all applications.", e);
            throw new RuntimeException("Failed to load all applications.", e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }


    @Override
    public List<ApplicationInfo> queryAllApplications(String userId) throws SQLException {
        List<ApplicationInfo> applications = new ArrayList<ApplicationInfo>();
        Connection connection = dbConnectUtil.getConnection();
        String sqlQuery = "select a.app_id,a.uid,a.app_code,a.create_time,a.sts, a.update_time from application_info a where a.uid = ? and a.sts= 'A'";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sqlQuery);
            pstmt.setString(1, userId);

            ResultSet results = pstmt.executeQuery();
            while (results.next()) {
                ApplicationInfo application = new ApplicationInfo(results.getString("app_id"));
                application.setUId(results.getString("uid"));
                application.setAppCode(results.getString("app_code"));
                application.setCreateTime(results.getTimestamp("create_time"));
                application.setSts(results.getString("sts"));
                application.setUpdateTime(results.getTimestamp("update_time"));
                applications.add(application);
            }
        } catch (Exception e) {
            logger.error("Failed to load all applications.", e);
            throw new RuntimeException("Failed to load all applications.", e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return applications;
    }

    @Override
    public void delApplication(String userId, String  applicationId) throws SQLException {
        Connection connection = dbConnectUtil.getConnection();
        String sqlQuery = "update application_info set sts=? where app_id = ? and uid = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sqlQuery);
            pstmt.setString(1, "D");
            pstmt.setString(2, applicationId);
            pstmt.setString(3, userId);

           pstmt.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to load all applications.", e);
            throw new RuntimeException("Failed to load all applications.", e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

}
