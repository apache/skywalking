package com.ai.cloud.skywalking.example.resource.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.sql.*;

@Component
public class ResourceDAO {
    private static Logger logger = LogManager.getLogger(ResourceDAO.class);
    private static final String URL = "tracing:jdbc:mysql://localhost:3306/test";
    private static final String DRIVER_CLASS = "com.ai.cloud.skywalking.plugin.jdbc.mysql.MySQLTracingDriver";
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";
    private static Connection con;

    static {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            logger.error("Cannot find the driver class", e);
        }
        try {
            con = DriverManager.getConnection(URL, USER_NAME, PASSWORD);
        } catch (SQLException e) {
            logger.error("Cannot connect the DB.", e);
        }
    }

    public String selectPhoneNumberStatus(String phoneNumber) {
        try {
            PreparedStatement preparedStatement = con.prepareStatement(
                    "SELECT STATUE FROM T_PHONE_NUMBER WHERE PHONE_NUMBER = ?");
            preparedStatement.setString(1, phoneNumber);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("STATUE");
            }
        } catch (SQLException e) {
            logger.error("Select phone number status failed", e);
        }
        return null;
    }

    public boolean updatePhoneNumberStatus(String phoneNumber) {
        try {
            PreparedStatement preparedStatement = con.prepareStatement(
                    "UPDATE t_phone_number SET STATUE = ?  WHERE PHONE_NUMBER = ?");
            preparedStatement.setString(1, "1");
            preparedStatement.setString(2, phoneNumber);
            preparedStatement.execute();
            return true;
        } catch (SQLException e) {
            logger.error("update phone number status failed", e);
        }
        return false;
    }

    public String selectResourceStatus(String resourceId) {
        try {
            PreparedStatement preparedStatement = con.prepareStatement(
                    "SELECT STATUE FROM T_RESOURCE WHERE RESOURCE_ID = ?");
            preparedStatement.setString(1, resourceId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("STATUE");
            }
        } catch (SQLException e) {
            logger.error("Select resource status failed.", e);
        }
        return null;
    }

    public boolean updateResourceStatus(String resourceId) {
        try {
            PreparedStatement preparedStatement = con.prepareStatement(
                    "UPDATE t_resource SET STATUE = ?  WHERE RESOURCE_ID = ?");
            preparedStatement.setString(1, "1");
            preparedStatement.setString(2, resourceId);
            preparedStatement.execute();
            return true;
        } catch (SQLException e) {
            logger.error("Update phone number status failed", e);
        }
        return false;
    }

    public String selectPhonePackageStatus(String packageId) {
        try {
            PreparedStatement preparedStatement = con.prepareStatement(
                    "SELECT STATUE FROM T_PHONE_PACKAGE WHERE PACKAGE_ID = ?");
            preparedStatement.setString(1, packageId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("STATUE");
            }
        } catch (SQLException e) {
            logger.error("Select phone package status failed.", e);
        }

        return null;
    }

    public boolean updatePhonePackageStatus(String packageId) {
        try {
            PreparedStatement preparedStatement = con.prepareStatement(
                    "UPDATE t_phone_package SET STATUE = ?  WHERE PACKAGE_ID = ?");
            preparedStatement.setString(1, "1");
            preparedStatement.setString(2, packageId);
            preparedStatement.execute();
            return true;
        } catch (SQLException e) {
            logger.error("Update phone number status failed", e);
        }
        return false;
    }
}
